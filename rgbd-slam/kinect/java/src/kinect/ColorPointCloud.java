package kinect;

import java.util.*;

import april.jmat.*;
import april.vis.*;
import kinect.Kinect.Frame;

public class ColorPointCloud {

    public ArrayList<double[]> points;
    public ArrayList<Integer> colors;
    public VisColorData vcd = new VisColorData();

    // hash map storing mapping between rgb image and depth
    //HashMap<Pixel, Double> rgbDmap = new HashMap<Pixel, Double>();
    int[] pointmap;

    private void buildArrays() {
        points = new ArrayList<double[]>(307200);
        colors = new ArrayList<Integer>(307200);
        pointmap = new int[Constants.HEIGHT * Constants.WIDTH];
    }

    public ColorPointCloud(Kinect.Frame frame) {
        //StopWatch sanityTimer = new StopWatch("Sanity Timer");
        //sanityTimer.start("The whole thing");
        //StopWatch outertimer = new StopWatch("ColorPointCloud ctor");

        //outertimer.start("Building Arrays");
        buildArrays();
        //outerimer.stop();

        for (int y = 0; y < Constants.HEIGHT; y++) {
            for (int x = 0; x < Constants.WIDTH; x++) {
                processPoint(frame, x, y);
            }
        }
        //outertimer.addTask(new StopWatch.TaskInfo("Computing M", computingM));
        //outertimer.addTask(new StopWatch.TaskInfo("Transforming", transforming));
        //outertimer.addTask(new StopWatch.TaskInfo("Adding point", addingPt));
        //outertimer.addTask(new StopWatch.TaskInfo("Setting pointmap", settingPointMap));
        //outertimer.addTask(new StopWatch.TaskInfo("Points 3D", points3D));
        //outertimer.addTask(new StopWatch.TaskInfo("Projecting", projecting));
        //outertimer.addTask(new StopWatch.TaskInfo("Adding colors", coloringTime));

        //System.out.println(outertimer.prettyPrint());
        //sanityTimer.stop();
        //System.out.println(sanityTimer.prettyPrint());
    }

    // alternate constructor for making a decimated point cloud
    // 1 is full resolution, 10 would be every 10 pixels
    // XXX fix for aliasing!
    public ColorPointCloud(Kinect.Frame frame, int Dfactor) {
        buildArrays();

        for (int y = 0; y < Constants.HEIGHT; y = y + Dfactor) {
            for (int x = 0; x < Constants.WIDTH; x = x + Dfactor) {
                processPoint(frame, x, y);
            }
        }
    }
    private long computingM = 0, transforming = 0, addingPt = 0, settingPointMap = 0, points3D = 0,
            projecting = 0, coloringTime = 0;

    protected final long[] processPoint(Frame frame, int x, int y) {
        // Calculate point place in world
        long[] values = new long[4];

        double m = frame.depthToMeters(frame.depth[y * Constants.WIDTH + x]);
        if (m < 0) {
            return new long[]{0, 0, 0, 0};
        }
        //values[0] = timer.getLastTaskTimeMillis();

        // points in 3D
        double px = (x - Constants.Cirx) * m / Constants.Firx;
        double py = (y - Constants.Ciry) * m / Constants.Firy;
        double pz = m;

        // Calculate color of point
        int cx = 0, cy = 0;

        // rotation transformation to transform from IR frame to RGB frame
        double[] xyz = new double[]{px, py, pz};
        double[] cxyz = LinAlg.transform(Constants.Transirtorgb, xyz);
        /*
        double[] cxyz = LinAlg.transform(Constants.Rirtorgb, xyz);
        cxyz = LinAlg.transform(Constants.Tirtorgb, cxyz);
         */

        // project 3D point into rgb image frame
        cx = (int) ((cxyz[0] * Constants.Frgbx / cxyz[2]) + Constants.Crgbx);
        cy = (int) ((cxyz[1] * Constants.Frgby / cxyz[2]) + Constants.Crgby);
        assert (!(cx < 0 || cx > Constants.WIDTH));
        assert (!(cy < 0 || cy > Constants.HEIGHT));

        points.add(new double[]{px, py, pz});

        pointmap[cy * Constants.WIDTH + cx] = points.size() - 1;

        int argb = frame.argb[cy * Constants.WIDTH + cx]; // get the rgb data for the calculated pixel location

        if(pz < 1000){
            colors.add(argb);
            int abgr = (argb & 0xff000000) | ((argb & 0xff) << 16) | (argb & 0xff00) | ((argb >> 16) & 0xff);
            vcd.add(abgr);
        }

        return values;
    }

    // projects image coordinates in rgb image into 3D space
    // XXX if ever make change to way color point cloud is projected into 3D
    // need to make that fix below
    public double[] Project(double[] Xrgb) {
        assert (Xrgb.length == 2);

        int index = pointmap[(int) (Xrgb[1] * Constants.WIDTH + Xrgb[0])];

        if (index == 0) {
            return new double[] {-1, -1, -1};
        }

        double[] P = this.points.get(index);

        assert(P.length == 3);
        return P;
    }

public ArrayList<double[]> Project(List<double[]> XRGB) {
        ArrayList<double[]> P = new ArrayList<double[]>();

       for (double[] Xrgb: XRGB) {
            double[] p = Project(Xrgb);
            P.add(p);
        }
        return P;
    }

    public int numPoints() {
        return points.size();
    }
}
