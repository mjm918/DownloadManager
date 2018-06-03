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
public class HttpReturn {
    
    public int _code;
    public long _contentLength;

    public HttpReturn(int _code, long _contentLength){
        this._code = _code;
        this._contentLength = _contentLength;
    }
    
}
