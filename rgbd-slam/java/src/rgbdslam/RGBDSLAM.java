package rgbdslam;


import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import lcm.lcm.*;

import april.vis.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;
import april.lcmtypes.*;

import kinect.*;

import rgbdslam.*;

public class RGBDSLAM implements LCMSubscriber
{
    LCM lcm = LCM.getSingleton();

    KinectThread kt;
    RenderThread rt;
    RGBDThread rgbd;

    double DEFAULT_RES = 0.01;
    double currRes = DEFAULT_RES;
    VoxelArray globalVoxelFrame = new VoxelArray(DEFAULT_RES);

    // State
    boolean loadedFromFile = false;

    // Gamepad_t
    ExpiringMessageCache<gamepad_t> lgp = new ExpiringMessageCache<gamepad_t>(0.25);

    public RGBDSLAM(GetOpt opts)
    {
        // Load from file
        if (opts.getString("file") != null) {
            System.out.println("Loading from file...");
            VoxelArray va = VoxelArray.readFromFile(opts.getString("file"));
            if (va != null) {
                globalVoxelFrame = va;
                loadedFromFile = true;
                System.out.println("Successfully loaded!");
            }
        }

        lcm.subscribe("GAMEPAD", this);

        kt = new KinectThread();
        rt = new RenderThread();
        rgbd = new RGBDThread();

        if (!loadedFromFile) {
            kt.start();
        }
        rt.start();
        if (!loadedFromFile) {
            rgbd.start();
        }
    }

    /** Receive LCM messages from the Gamepad */
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            if (channel.equals("GAMEPAD")) {
                gamepad_t gp = new gamepad_t(ins);
                lgp.put(gp, gp.utime);
            }
        } catch (IOException ex) {
            System.err.println("ERR: Failed to decoded message on: "+channel);
        }
    }

    class KinectThread extends Thread
    {
        int pollRate = 5;

        boolean closeSignal = false;
        boolean closed = true;

        public void run()
        {
            closed = false;
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
            System.out.println("Kinect closed");

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

        // Gamepad
        boolean turbo = false;
        double vel = 2.5;
        double theta_vel = Math.toRadians(15);

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
            pg.addButtons("save", "Save to file");
            pg.addListener(new ParameterListener() {
                public void parameterChanged(ParameterGUI pg, String name) {
                    if (name.equals("resolution") && !loadedFromFile) {
                        //updateVoxelRes(pg.gd("resolution"));
                    } else if (name.equals("save")) {
                        long time = System.currentTimeMillis();
                        synchronized (globalVoxelFrame) {
                            String filename = "va_"+time+".vx";
                            globalVoxelFrame.writeToFile(filename);
                            System.out.println("Saved to "+filename);
                        }
                    } else {
                        updateFPS();
                    }
                }
            });
            updateFPS();

            //VzGrid.addGrid(vw);


            // XXX May need to change default position. Moving still
            // glitchy. Twitches and doesn't respect our axes
            DefaultCameraManager dcm = new DefaultCameraManager();
            dcm.UI_ANIMATE_MS = 25;
            dcm.interfaceMode = 3.0;
            double[] eye = new double[] {-10,0,0};
            double[] lookat = new double[] {-9,0,0};
            double[] up = new double[] {0,0,1};
            dcm.uiLookAt(eye, lookat, up, true);
            vl.cameraManager = dcm;

            jf.add(vc, BorderLayout.CENTER);
            jf.add(pg, BorderLayout.SOUTH);

            jf.setVisible(true);
        }

        synchronized public void run()
        {
            Tic tic = new Tic();
            while (true) {
                // XXX Some bugs
                double[] xyzrpy = getCameraXYZRPY(tic.toctic());
                if (vc.getLastRenderInfo() != null) {
                    VisCameraManager.CameraPosition cpos = vc.getLastRenderInfo().cameraPositions.get(vl);

                    double[] yaxis = LinAlg.normalize(cpos.up);
                    double[] nzaxis = LinAlg.normalize(LinAlg.subtract(cpos.lookat, cpos.eye));
                    double[] xaxis = LinAlg.normalize(LinAlg.crossProduct(nzaxis, yaxis));

                    double[][] rotz = LinAlg.quatToMatrix(LinAlg.angleAxisToQuat(xyzrpy[3], nzaxis));
                    double[][] rotx = LinAlg.quatToMatrix(LinAlg.angleAxisToQuat(xyzrpy[4], xaxis));
                    double[][] roty = LinAlg.quatToMatrix(LinAlg.angleAxisToQuat(xyzrpy[5], yaxis));

                    // Translation
                    double[] eye = LinAlg.copy(cpos.eye);
                    double[] lookat = LinAlg.copy(cpos.lookat);
                    double x = xyzrpy[0];
                    double y = xyzrpy[1];
                    double z = xyzrpy[2];
                    double[] dx = new double[] {x*xaxis[0], x*xaxis[1], x*xaxis[2]};
                    double[] dy = new double[] {y*yaxis[0], y*yaxis[1], y*yaxis[2]};
                    double[] dz = new double[] {z*nzaxis[0], z*nzaxis[1], z*nzaxis[2]};

                    eye = LinAlg.add(dx, LinAlg.add(dy, LinAlg.add(dz, eye)));
                    lookat = LinAlg.add(dx, LinAlg.add(dy, LinAlg.add(dz, lookat)));

                    double[][] eye_trans = LinAlg.translate(eye);
                    double[][] eye_inv = LinAlg.inverse(eye_trans);

                    LinAlg.timesEquals(roty, eye_inv);
                    LinAlg.timesEquals(rotx, roty);
                    LinAlg.timesEquals(rotz, rotx);
                    LinAlg.timesEquals(eye_trans, rotz);

                    lookat = LinAlg.transform(eye_trans, lookat);

                    vl.cameraManager.uiLookAt(eye, lookat, cpos.up, false);
                }

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

        private double[] getCameraXYZRPY(double dt)
        {
            gamepad_t gp = lgp.get();
            if (gp == null)
                return new double[6];
            if ((gp.buttons & 0xF0) > 1 && !turbo) {
                vel = 5.0;
                theta_vel = Math.toRadians(45);
                turbo = true;
            } else if ((gp.buttons & 0xF0) == 0 && turbo) {
                vel = 1.0;
                theta_vel = Math.toRadians(15);
                turbo = false;
            }

            return new double[] {gp.axes[0]*vel*dt, gp.axes[5]*-vel*dt, gp.axes[1]*-vel*dt, gp.axes[4]*-theta_vel*dt, gp.axes[3]*-theta_vel*dt, gp.axes[2]*-theta_vel*dt};
        }
    }

    class RGBDThread extends Thread
    {
        Kinect.Frame currFrame = null;
        Kinect.Frame lastFrame = null;

        synchronized public void run()
        {
            double[][] rbt = Matrix.identity(4,4).copyArray();

            while (true) {
                if (currFrame != null && lastFrame != null) {
                    // Deal with new data
                    // XXX Temporary
                    ColorPointCloud cpc = new ColorPointCloud(currFrame);
                    VoxelArray va = new VoxelArray(DEFAULT_RES); // XXX

                    // Extract features, perform RANSAC and ICP // XXX No ICP yet
                    double[][] ransac = getTransform(cpc);
                    LinAlg.timesEquals(ransac, rbt);
                    rbt = ransac;

                    ICP icp = new ICP(new ColorPointCloud(lastFrame, 10));
                    double[][] transform = icp.match(new ColorPointCloud(currFrame, 10), Matrix.identity(4,4).copyArray());
                    LinAlg.timesEquals(transform, rbt);
                    rbt = transform;

                    va.voxelizePointCloud(cpc);

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
            lastFrame = currFrame;
            currFrame = frame;
            notifyAll();
        }

        private double[][] getTransform(ColorPointCloud cpc)
        {
            int[] argbC = currFrame.argb;
            int[] argbL = lastFrame.argb;
            ArrayList<ImageFeature> featuresC = OpenCV.extractFeatures(argbC, 640);
            ArrayList<ImageFeature> featuresL = OpenCV.extractFeatures(argbL, 640);

            // Set xyz (world) coordinates
            for(ImageFeature fc: featuresC){
                fc.setXyz(cpc.Project(LinAlg.copyDoubles(fc.xy())));
            }
            for(ImageFeature fl: featuresL){
                fl.setXyz(cpc.Project(LinAlg.copyDoubles(fl.xy())));
            }

            // Match SIFT features -> RANSAC
            DescriptorMatcher dm = new DescriptorMatcher(featuresL, featuresC);
            ArrayList<DescriptorMatcher.Match> matches = dm.match();
            ArrayList<DescriptorMatcher.Match> inliers = new ArrayList<DescriptorMatcher.Match>();

            return RANSAC.RANSAC(matches, inliers);
        }
    }

    static public void main(String[] args)
    {
        GetOpt opts = new GetOpt();
        opts.addBoolean('h', "help", false, "Show this help screen");
        opts.addString('f', "file", null, "Load scene from file");

        if (!opts.parse(args)) {
            System.err.println("ERR: Opts error - " + opts.getReason());
            System.exit(1);
        }

        if (opts.getBoolean("help")) {
            opts.doHelp();
            System.exit(1);
        }

        RGBDSLAM rgbdSLAM = new RGBDSLAM(opts);
    }
}
