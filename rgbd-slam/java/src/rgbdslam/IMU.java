/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rgbdslam;

import april.util.*;
import april.jmat.*;
import kinect.StopWatch;

/**
 *
 * @author jrpeterson
 */

/* a virtual inertial measuremet unit which attempts to enforce a constant velocity model
 * on kinect motion. 
 */
public class IMU {

    StopWatch time = new StopWatch();
    double[] Vxyzrpy; // x y z roll pitch yaw velocity
    private static final double ALPHA = 1; // relative weighting between accumulatd data and new measurement
    // 0 means only care about new information, Infinity means never update ever
    private static final double BETA = 5;
    private static final double DELTAV_THRESH = 2; // if our velocity changes by a factor of 50% throw it out
    private static final double DELTAR_THRESH = 2; // if our angular velocity changes by 25% throw it out
    private static final double LV_THRESH = 0.02; // if we are dealing with small velocities 2 cm per second
    private static final double LR_THRESH = 0.17; // 10 degrees per second
    private boolean flag = false; // did we use the IMU to estimate our motion for initializing ICP
    // if so we want to handle things a bit differently
    
    private double T; // stores time elaspsed for computing velocity;

    public IMU() {
        time = new StopWatch();
        time.start();
        Vxyzrpy = new double[6];
    }
    
    // saves time elaspsed so we know how much time was spent moving between images
    public void mark() {
        time.stop();
        T = (double) time.getLastTaskTimeMillis();
        T /= 1000; // convert to seconds
        time.start();
    }

    // if we don't have anything to filter, just use Dead Reckoning
    public double[][] estimate() {
        // use new velocity estimates to estimate xyzrpy
        double[] Exyzrpy = LinAlg.scale(Vxyzrpy, T);

        // convert back to RBT
        double[][] Erbt = LinAlg.xyzrpyToMatrix(Exyzrpy);
        // XXX there is a 2nd version of the function above which one is better
        return Erbt;
    }

    // incorporate new information and return new estimate
    public double[][] estimate(double[][] Urbt) {
        // what rigid body transformation occured over the last frame
        double[] DUxyzrpy = LinAlg.matrixToXyzrpy(Urbt);

        // convert to velocity
        double[] Uxyzrpy;
        if (T != 0) {
            Uxyzrpy = LinAlg.scale(DUxyzrpy, 1 / T);
        } else {
            Uxyzrpy = new double[6];
        }
        /*
        System.out.println("Our observation velocity");
        LinAlg.print(Uxyzrpy);
        
        System.out.println("Our best estimate of velocity");
        LinAlg.print(Vxyzrpy);
         */
        // calculate relative weighting
        double[] W = constructW(Uxyzrpy);
        /*
        System.out.println("Our weighting matrix");
        LinAlg.print(W);
         */
        // incorporatenew information
        Vxyzrpy = weightedSum(Vxyzrpy, Uxyzrpy, W);
        /*
        System.out.println("After in corporating information");
        LinAlg.print(Vxyzrpy);
         */
        // use new velocity estimates to estimate xyzrpy
        double[] Exyzrpy = LinAlg.scale(Vxyzrpy, T);

        // convert back to RBT
        double[][] Erbt = LinAlg.xyzrpyToMatrix(Exyzrpy);
        // XXX there is a 2nd version of the function above which one is better
        return Erbt;
    }

    // weight A by alpha and B by 1-alpha if alpha == 1 then returns A
    private double[] weightedSum(double[] A, double[] B, double[] W) {
        assert ((A.length == B.length) && (A.length == W.length)) : "Warning!";
        double[] C = new double[A.length];
        for (int i = 0; i < A.length; i++) {
            C[i] = (1 - W[i]) * A[i] + W[i] * B[i];
            if (C[i] == Double.NaN) {
                System.out.println("Warning generated NaN: T = " + T);
                LinAlg.print(W);
            }
        }
        return C;
    }

    // incorporates exponential model of probability of motion, weight less likely motions less
    private double[] constructW(double[] Uxyzrpy) {
        assert (Uxyzrpy.length == Vxyzrpy.length) : "Warning expected 6 velocity";

        double[] W = new double[6]; // weighting matrix

        for (int i = 0; i < Uxyzrpy.length; i++) {
            double u = Uxyzrpy[i];
            double v = Vxyzrpy[i];
            double Dv = Math.abs(u - v);

            if (i <= 2) {
                // handle velocities close to 0
                if ((Math.abs(u) <= LV_THRESH) && (Math.abs(v) <= LV_THRESH)) {
                    // use exponential model to come up with weights
                    W[i] = MathUtil.exp(-ALPHA * Dv);
                } // if our change in velocity was within acceptable bounds
                else if ((Dv / u) <= DELTAV_THRESH) {
                    // use exponential model to come up with weights
                    W[i] = MathUtil.exp(-ALPHA * Dv);
                } else {
                    W[i] = 0; // throw out bad changes
                }
            } else {
                // handle angular velocities close to 0
                if ((Math.abs(u) <= LR_THRESH) && (Math.abs(v) <= LR_THRESH)) {
                    // use exponential model to come up with weights
                    W[i] = MathUtil.exp(-BETA * Dv);
                } // if our change in velocity was within acceptable bounds
                else if ((Dv / u) <= DELTAR_THRESH) {
                    // use exponential model to come up with weights
                    W[i] = MathUtil.exp(-BETA * Dv);
                } else {
                    W[i] = 0; // throw out bad changes
                }
            }
        }
        return W;
    }
}
