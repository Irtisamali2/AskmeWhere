
package org.blind.help.object.detection.tflite;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;


public interface Classifier {
    List<Recognition> recognizeImage(Bitmap bitmap);


    void setNumThreads(int num_threads);

    void setUseNNAPI(boolean isChecked);



    class Recognition {

        private final String id;


        private final String title;

        private final Float confidence;

        private RectF location;

        private int detectedClass;
        public String className;



        public Recognition(final String id, final String title, final Float confidence, final RectF location, int detectedClass,String className) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
            this.detectedClass = detectedClass;
            this.className= className;
        }


        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        public int getDetectedClass() {
            return detectedClass;
        }


        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }
}
