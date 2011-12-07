package kinect;

import java.util.*;

import april.jmat.*;
import april.vis.*;
import kinect.Kinect.Frame;

public class ColorPointCloud
{
    public ArrayList<double[]> points = new ArrayList<double[]>();
    public ArrayList<Integer> colors = new ArrayList<Integer>();
    public VisColorData vcd = new VisColorData();

    // hash map storing mapping between rgb image and depth
    HashMap<Pixel, Double> rgbDmap = new HashMap<Pixel, Double>();

    public ColorPointCloud(Kinect.Frame frame)
    {
        assert (frame != null);
        for (int y = 0; y < Constants.HEIGHT; y++) {
            for (int x = 0; x < Constants.WIDTH; x++) {
                processPoint(frame, x, y);
            }
        }
    }

    // alternate constructor for making a decimated point cloud
    // 1 is full resolution, 10 would be every 10 pixels
    // XXX fix for aliasing!
    public ColorPointCloud(Kinect.Frame frame, int Dfactor) {
        for (int y = 0; y < Constants.HEIGHT; y = y + Dfactor) {
            for (int x = 0; x < Constants.WIDTH; x = x + Dfactor) {
                processPoint(frame, x, y);
            }
        }
    }


    protected final boolean processPoint(Frame frame, int x, int y) {
        // Calculate point place in world
        double m = frame.depthToMeters(frame.depth[y*Constants.WIDTH + x]);
        if (m < 0) {
            return true;
        }
        // points in 3D
        double px = (x - Constants.Cirx) * m/Constants.Firx;
        double py = (y - Constants.Ciry) * m/Constants.Firy;
        double pz = m;
        // Calculate color of point
        int cx = 0, cy = 0;
        // rotation transformation to transform from IR frame to RGB frame
        double[] xyz = new double[] {px, py, pz};
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
        points.add(new double[] {pz, -px, -py});
        int argb = frame.argb[cy*Constants.WIDTH + cx]; // get the rgb data for the calculated pixel location
        // add to hashmap cx cy which maps to m
        rgbDmap.put(new Pixel(cx,cy), m);
        // this is a test of the mapping
        /*
        if (rgbDmap.containsKey(new Pixel(cx,cy))) {
            double depth = rgbDmap.get(new Pixel(cx,cy));
            System.out.println("Yep it works got D = " + depth);
            } */
        colors.add(argb);
        int abgr = (argb & 0xff000000) | ((argb & 0xff) << 16) | (argb & 0xff00) | ((argb >> 16) & 0xff);
        vcd.add(abgr);
        return false;
    }


    // projects image coordinates in rgb image into 3D space
    // XXX if ever make change to way color point cloud is projected into 3D
    // need to make that fix below
    public double[] Project(double[] Xrgb) {
        assert (Xrgb.length == 2);
        Pixel key = new Pixel((int) Xrgb[0],(int) Xrgb[1]);

        assert (!rgbDmap.isEmpty());

        if (rgbDmap.containsKey(key)) {
            double m = rgbDmap.get(key);

            // handle points in depth image without a depth value
            if (m < 0) {
                //System.out.println("Depth less than 0!");
                return new double[] {-1, -1, -1};
            }

            double[] P = new double[3];
            P[0] = (Xrgb[0] - Constants.Crgbx) * m/Constants.Frgbx;
            P[1] = (Xrgb[1] - Constants.Crgby) * m/Constants.Frgby;
            P[2] = m;

            return P;
            // handle points without a mapping to the depth image
        } else {
            //System.out.println("Couldn't find the key!");
            return new double[] {-1, -1, -1};
        }
    }

    public ArrayList<double[]> Project(List<double[]> XRGB) {

        ArrayList<double[]> P = new ArrayList<double[]>();

       for (double[] Xrgb: XRGB) {
            double[] p = Project(Xrgb);
            P.add(p);
        }
        return P;
    }

    public int numPoints()
    {
        return points.size();
    }
}
