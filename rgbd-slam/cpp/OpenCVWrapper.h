/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class rgbdslam_OpenCV */

#ifndef _Included_rgbdslam_OpenCV
#define _Included_rgbdslam_OpenCV
#ifdef __cplusplus
extern "C" {
#endif
#undef rgbdslam_OpenCV_DEFAULT_MAX_FEATURES
#define rgbdslam_OpenCV_DEFAULT_MAX_FEATURES 100L
#undef rgbdslam_OpenCV_DEFAULT_MIN_QUALITY
#define rgbdslam_OpenCV_DEFAULT_MIN_QUALITY 0.01
#undef rgbdslam_OpenCV_DEFAULT_MIN_DISTANCY
#define rgbdslam_OpenCV_DEFAULT_MIN_DISTANCY 1L
#undef rgbdslam_OpenCV_DEFAULT_BLOCK_SIZE
#define rgbdslam_OpenCV_DEFAULT_BLOCK_SIZE 3L
/*
 * Class:     rgbdslam_OpenCV
 * Method:    cvExtractFeatures
 * Signature: ([IIIDDI[I[I[D)I
 */
JNIEXPORT jint JNICALL Java_rgbdslam_OpenCV_cvExtractFeatures
  (JNIEnv *, jclass, jintArray, jint, jint, jdouble, jdouble, jint, jintArray, jintArray, jdoubleArray);

/*
 * Class:     rgbdslam_OpenCV
 * Method:    cvGetDescriptorSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_rgbdslam_OpenCV_cvGetDescriptorSize
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif