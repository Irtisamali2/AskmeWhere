package org.blind.help.object.detection.distance;

import org.blind.help.object.detection.CameraActivity;
import org.blind.help.object.detection.DetectorActivity;

public class DistanceModel {
    float measureDistanceFromCameraInInches;
    float measureWidthOfObjectInInches;
    float measureWidthOfObjectInPixels;
    String objectName;
   DistanceModel(String objectName,float measureDistanceFromCameraInInches, float measureWidthOfObjectInInches, float measureWidthOfObjectInPixels) {
        this.measureDistanceFromCameraInInches = measureDistanceFromCameraInInches;
        this.measureWidthOfObjectInInches = measureWidthOfObjectInInches;
        this.measureWidthOfObjectInPixels = measureWidthOfObjectInPixels;
        this.objectName = objectName;
    }
    //F  = (P x D) / W
    public float calcuteFocalLengthInPixels() {
        return (measureWidthOfObjectInPixels * measureDistanceFromCameraInInches) / measureWidthOfObjectInInches;
    }
    // calculate the distance from the camera to the object in inches D’ = (W x F) / P
    public float calcuteDistanceFromCameraInInches( float precivedWidthOfObjectInPixels) {
        return (measureWidthOfObjectInInches * calcuteFocalLengthInPixels()) / precivedWidthOfObjectInPixels;
    }

    public float calcuteDistanceFromCameraInInchesUsingFocal( float precivedWidthOfObjectInPixels) {

        return (float) ((measureWidthOfObjectInInches * DetectorActivity.focal) / precivedWidthOfObjectInPixels);
    }


}
