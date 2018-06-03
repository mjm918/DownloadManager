/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

/**
 *
 * @author julfikar
 */
public class Handler {
    
    public static String convertToUOM(double bytes){
        String[] UOM = {"KB", "MB", "Mb", "GB", "TB", "PB", "EB"};
        String uomInWord = "";

        double _size = bytes;
        int _counter = 0;
        while (_size > 1024 && _counter < UOM.length){
            _size = _size / 1024;
            _counter++;
        }

        _size = (double) Math.round(_size*100) / 100;
        uomInWord += String.valueOf(_size) + " " + UOM[_counter];

        return uomInWord;
    }
    
}
