package rgbdslam;

import java.util.*;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import april.vis.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

import kinect.*;

import rgbdslam.*;

public class RGBDSLAM
{
    KinectThread kt;
    RenderThread rt;
    RGBDThread rgbd;

    double DEFAULT_RES = 0.05;
    double currRes = DEFAULT_RES;
    VoxelArray globalVoxelFrame = new VoxelArray(DEFAULT_RES);

    public RGBDSLAM(GetOpt opts)
    {
        // XXX Load from file
        if (opts.getString("file") != null) {

        }

        kt = new KinectThread();
        rt = new RenderThread();
        rgbd = new RGBDThread();
        kt.start();
        rt.start();
        rgbd.start();
    }

    class KinectThread extends Thread
    {
        int pollRate = 5;

        boolean closeSignal = false;
        boolean closed = false;

        public void run()
        {
            Kinect kinect = new Kinect();

            kinect.init();
            kinect.start();

            while (true) {
                Kinect.Frame f = kinect.getFrame();
                if (f != null) {
                    // XXX Might want to dump frames
                    rgbd.handleFrame(f);
                }

                if (closeSignal)
                    break;

                TimeUtil.sleep(1000/pollRate);
            }

            System.out.println("Stopping kinect...");
            kinect.stop();
            System.out.println("Kinect stopped");
            System.out.println("Closing kinect...");
            kinect.close();
            System.out.println("Kienct closed");

            closed = true;
        }

        synchronized public void close()
        {
            closeSignal = true;
        }

        synchronized public boolean isClosed()
        {
            return closed;
        }

        synchronized public void setPollRate(int pr)
        {
            pollRate = pr;
        }
    }

    class RenderThread extends Thread
    {
        int fps = 5;

        // Vis
        VisWorld vw;
        VisLayer vl;
        VisCanvas vc;

        // Knobs
        ParameterGUI pg;

        // Flying around stuff
        double xticks, yticks, zticks, tticks;
        double vel = 1.0; // m/s
        double theta_vel = Math.toRadians(15);  // rad/sec

        public RenderThread()
        {
            vw = new VisWorld();
            vl = new VisLayer(vw);
            vc = new VisCanvas(vl);

            JFrame jf = new JFrame("RGBDSLAM Visualization");
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setSize(1280, 720);
            jf.setLayout(new BorderLayout());

            // Make sure kinect gets closed
            jf.addWindowListener(new WindowAdapter() {
                synchronized public void windowClosing(WindowEvent e) {
                    System.out.println("Sending kinect close message...");
                    kt.close();
                    while (!kt.isClosed()) {
                        try {
                            System.out.println("Kinect not yet closed...");
                            wait(1000);
                        } catch (InterruptedException ex) {}
                    }
                    System.out.println("Shutdown complete");
                    System.out.println("Goodbye!");
                }
            });

            pg = new ParameterGUI();
            pg.addDoubleSlider("resolution", "Voxel Resolution (m)", 0.005, 0.5, DEFAULT_RES);
            pg.addIntSlider("kfps", "Kinect FPS", 1, 30, 5);
            pg.addIntSlider("rfps", "Render FPS", 1, 60, 15);
            pg.addListener(new ParameterListener() {
                public void parameterChanged(ParameterGUI pg, String name) {
                    if (name.equals("resolution")) {
                        //updateVoxelRes(pg.gd("resolution"));
                    } else {
                        updateFPS();
                    }
                }
            });
            updateFPS();

            VzGrid.addGrid(vw);


            // XXX May need to change default position. Moving still
            // glitchy. Twitches and doesn't respect our axes
            /*DefaultCameraManager dcm = new DefaultCameraManager();
            dcm.UI_ANIMATE_MS = 25;
            vl.cameraManager = dcm;
            vl.cameraManager.setDefaultPosition(new double[] {0, 0, 5}, new double[] {0, 0, 0}, new double[] {0, 1, 0});
            vl.cameraManager.uiDefault();
            vl.addEventHandler(new MyEventAdapter());*/

            jf.add(vc, BorderLayout.CENTER);
            jf.add(pg, BorderLayout.SOUTH);

            jf.setVisible(true);
        }

        synchronized public void run()
        {
            while (true) {
                synchronized (globalVoxelFrame) {
                    if (globalVoxelFrame.size() > 0) {
                        VisWorld.Buffer vb = vw.getBuffer("voxels");
                        vb.addBack(new VisLighting(false,
                                                   globalVoxelFrame.getPointCloud()));
                        vb.swap();
                    }
                }

                TimeUtil.sleep(1000/fps);
            }
        }

        public void updateFPS()
        {
            fps = pg.gi("rfps");
            kt.setPollRate(pg.gi("kfps"));
        }


        synchronized public void toggleDirection(int vk)
        {
            if (vk == KeyEvent.VK_DOWN) {
                yticks -= 1;
            }
            if (vk == KeyEvent.VK_UP) {
                yticks += 1;
            }

            if (vk == KeyEvent.VK_W) {
                zticks += 1;
            }
            if (vk == KeyEvent.VK_S) {
                zticks -= 1;
            }

            if (vk == KeyEvent.VK_D) {
                xticks += 1;
            }
            if (vk == KeyEvent.VK_A) {
                xticks -= 1;
            }

            if (vk == KeyEvent.VK_LEFT) {
                tticks += 1;
            }
            if (vk == KeyEvent.VK_RIGHT) {
                tticks -= 1;
            }
            System.out.printf("[x:%f] [y:%f] [z:%f] [t:%f]\n", xticks, yticks, zticks, tticks);

        }


    }

    class RGBDThread extends Thread
    {
        Kinect.Frame currFrame = null;

        synchronized public void run()
        {
            Matrix rbt = Matrix.identity(4,4);

            while (true) {
                if (currFrame != null) {
                    // Deal with new data
                    // XXX Temporary
                    ColorPointCloud cpc = new ColorPointCloud(currFrame);
                    VoxelArray va = new VoxelArray(DEFAULT_RES); // XXX

                    // Process for features

                    // RANSAC
                    va.voxelizePointCloud(cpc);

                    // ICP

                    // Let render thread do its thing
                    synchronized (globalVoxelFrame) {
                        globalVoxelFrame.merge(va, rbt);
                    }
                }

                try {
                    wait();
                } catch (InterruptedException ex) {}
            }
        }

        synchronized public void handleFrame(Kinect.Frame frame)
        {
            currFrame = frame;
            notifyAll();
        }
    }

    class MyEventAdapter extends VisEventAdapter
    {
        // Deal with repeated key presses
        public boolean keyPressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
        {
            int vk = e.getKeyCode();
            rt.toggleDirection(vk);

            return false;
        }
    }

    static public void main(String[] args)
    {
        GetOpt opts = new GetOpt();
        opts.addBoolean('h', "help", false, "Show this help screen");
        opts.addString('f', "file", null, "Load scene from file");

        if (opts.getBoolean("help")) {
            opts.doHelp();
        }

        RGBDSLAM rgbdSLAM = new RGBDSLAM(opts);
    }
}
