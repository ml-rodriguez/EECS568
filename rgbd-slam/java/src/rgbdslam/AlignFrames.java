package rgbdslam;

import april.jmat.LinAlg;
import java.util.ArrayList;
import java.util.List;
import kinect.*;
import kinect.Kinect.Frame;
import rgbdslam.DescriptorMatcher.Match;

/**
 *
 * @author pdaquino
 */
public class AlignFrames {

    public static final int DECIMATION_FACTOR = 10;
    final static double ALPHA = 0.5; // relative weighting between initial estimate and new rbt estimate
    public final static int MIN_RANSAC_INLIERS = 20;

    private List<ImageFeature> currFeatures, lastFeatures;
    private ColorPointCloud currFullPtCloud, currDecimatedPtCloud;
    private ColorPointCloud lastFullPtCloud, lastDecimatedPtCloud;
    
    public class RBT {
        public double[][] rbt;
        public List<DescriptorMatcher.Match> allMatches = new ArrayList<Match>();
        public List<DescriptorMatcher.Match> inliers = new ArrayList<Match>();
    }

    public AlignFrames(Kinect.Frame currFrame, Kinect.Frame lastFrame) {
        this.currFullPtCloud = makeFullPtCloud(currFrame);
        this.currFeatures = extractAndProjectFeatures(currFrame, currFullPtCloud);
        this.currDecimatedPtCloud = makeDecimatedPtCloud(currFrame);

        this.lastFullPtCloud = makeFullPtCloud(lastFrame);
        this.lastFeatures = extractAndProjectFeatures(lastFrame, lastFullPtCloud);
        this.lastDecimatedPtCloud = makeDecimatedPtCloud(lastFrame);
    }

    public AlignFrames(Kinect.Frame currFrame, List<ImageFeature> lastProjectedFeatures,
            ColorPointCloud lastFullPtCloud, ColorPointCloud lastDecimatedPtCloud) {
        this.currFullPtCloud = makeFullPtCloud(currFrame);
        this.currFeatures = extractAndProjectFeatures(currFrame, currFullPtCloud);
        this.currDecimatedPtCloud = makeDecimatedPtCloud(currFrame);

        this.lastFeatures = lastProjectedFeatures;
        this.lastFullPtCloud = lastFullPtCloud;
        this.lastDecimatedPtCloud = lastDecimatedPtCloud;
    }

    public RBT align(double[][] previousTransform) {
        RBT rbt = new RBT();
        
        DescriptorMatcher dm = new DescriptorMatcher(currFeatures, lastFeatures);
        rbt.allMatches = dm.match();
        
        double[] Arpy = new double[3]; // average roll pitch yaw
        
        double[][] Rrbt = RANSAC.RANSAC(rbt.allMatches, rbt.inliers); // RANSAC's Estimate
        if (rbt.inliers.size() < MIN_RANSAC_INLIERS || Rrbt == null) {
            Rrbt = previousTransform;
        }
        
        System.out.println("RANSAC's estimate Roll Pitch Yaw");
        LinAlg.print(LinAlg.matrixToRollPitchYaw(Rrbt));
        LinAlg.print(Rrbt);
        System.out.println("RANSAC's translation maginitude " + TransMag(Rrbt));
        System.out.println("RANSAC's transformation matrix");
        LinAlg.print(Rrbt);
        
        RGBDICP icp = new RGBDICP(lastDecimatedPtCloud);

        double[][] Irbt = icp.match(currDecimatedPtCloud, Rrbt, rbt.inliers); // ICP's Estimate
        
        System.out.println("ICP's estimate Roll Pitch Yaw");
        LinAlg.print(LinAlg.matrixToRollPitchYaw(Irbt));

        System.out.println("ICP's translation maginitude " + TransMag(Irbt));
        System.out.println("ICP's transformation matrix");
        LinAlg.print(Irbt);

        //double[][] Erbt = weightedSum(Rrbt, Irbt, ALPHA);
        rbt.rbt = Irbt;
        
        // supress large translations cause they cause trouble
        System.out.println("Final Estimate");
        LinAlg.print(rbt.rbt);

        return rbt;
    }

    public ColorPointCloud getLastDecimatedPtCloud() {
        return lastDecimatedPtCloud;
    }

    public List<ImageFeature> getLastFeatures() {
        return lastFeatures;
    }

    public ColorPointCloud getLastFullPtCloud() {
        return lastFullPtCloud;
    }

    public ColorPointCloud getCurrDecimatedPtCloud() {
        return currDecimatedPtCloud;
    }

    public List<ImageFeature> getCurrFeatures() {
        return currFeatures;
    }

    public ColorPointCloud getCurrFullPtCloud() {
        return currFullPtCloud;
    }
   
    private ColorPointCloud makeDecimatedPtCloud(Frame frame) {
        return new ColorPointCloud(frame, DECIMATION_FACTOR);
    }

    private ColorPointCloud makeFullPtCloud(Frame frame) {
        return new ColorPointCloud(frame);
    }

    public static List<ImageFeature> extractAndProjectFeatures(Frame frame, ColorPointCloud fullPtCloud) {
        List<ImageFeature> allFeatures = OpenCV.extractFeatures(frame.argb, Constants.WIDTH);
        List<ImageFeature> features = new ArrayList<ImageFeature>();
        for (ImageFeature fc : allFeatures) {
            fc.setXyz(fullPtCloud.Project(LinAlg.copyDoubles(fc.xy())));
            if (fc.xyz()[2] != -1) {
                features.add(fc);
            }
        }
        return features;
    }
    
    // weight A by alpha and B by 1-alpha
    // if alpha == 1 then returns A
    private double[][] weightedSum(double[][] A, double[][] B, double alpha) {

        assert ((A.length == B.length) && (A[0].length == B[0].length) && (alpha <= 1) && (alpha >= 0));

        double[][] C = new double[A.length][A[0].length];
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[0].length; j++) {
                C[i][j] = alpha * A[i][j] + (1 - alpha) * B[i][j];
            }
        }
        return C;
    }
    
    // returns the magnitude of a translation encoded in 4x4 Rbt
    private double TransMag(double[][] A) {
        return Math.sqrt(A[0][3]*A[0][3] + A[1][3]*A[1][3] + A[2][3]*A[2][3]);
    }
}
