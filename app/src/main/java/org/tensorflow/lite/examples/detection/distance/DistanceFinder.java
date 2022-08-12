package org.tensorflow.lite.examples.detection.distance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DistanceFinder {
    //create array list of DistanceModel objects person, car, microwave, cell phone, apple,cat,mug, platter, watch, AND remote
    public  static ArrayList<String> _class = new ArrayList<String>(){
        {
            add("Person");
            add("Car");
            add("Microwave oven");
            add("Mobile phone");
            add("Apple");
            add("Cat");
            add("Mug");
            add("Platter");
            add("Watch");
            add("Remote control");
        }
    };
    public static String getDistanceInInches(String objectName, float widthInPixels){
        if(DistanceFinder._class.contains(objectName)) {
            // result upto 2 floating point precision
            switch (objectName){
                case "Person":// average 15 inches
                    return String.format("%.02f",new DistanceModel("person", 147.606f, 15.0f, 227.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10 )+ " inches";
                case "Car":
                    return String.format("%.02f",new DistanceModel("car", 147.606f, 69.6f, 1049.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10 )+ " inches";
                case "Microwave oven":
                    return String.format("%.02f",new DistanceModel("microwave", 147.606f, 30.0f, 452.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";
                case "Mobile phone":// working pretty good.
                    return String.format("%.02f",new DistanceModel("Mobile phone", 147.606f, 5.5f, 84.0f).calcuteDistanceFromCameraInInches(widthInPixels)/10 )+ " inches";
                case "Apple":
                    return String.format("%.02f",new DistanceModel("apple", 147.606f, 3.5f, 52.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10 )+ " inches";
                case "Cat":
                    return String.format("%.02f",new DistanceModel("cat", 147.606f, 2.75591f, 42.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";
                case "Mug":
                    return String.format("%.02f",new DistanceModel("mug", 147.606f, 2.99213f, 44.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";
                case "Platter":
                    return String.format("%.02f",new DistanceModel("platter", 147.606f, 9.75f, 147.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";
                case "Watch":
                    return String.format("%.02f",new DistanceModel("watch", 147.606f, 1.65354f, 25.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";
                case "Remote control":
                    return String.format("%.02f",new DistanceModel("remote", 147.606f, 1.43f, 21.5f).calcuteDistanceFromCameraInInches(widthInPixels)/10) + " inches";


            }

        }
        return " ";
    }



}
