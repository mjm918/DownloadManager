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
public class Session {
    
    private final String _filename;
    private final String _url;
    private long _downloadedSize;
    public boolean _alreadyDownloaded;
    public boolean _resume;
    public boolean _cancel;

    Session(String _filename,String _url, long _downloadedSize){
        this._downloadedSize = _downloadedSize;
        this._url = _url;
        this._filename = _filename;
    }

    public String get_filename() {
        return _filename;
    }

    public String get_url() {
        return _url;
    }

    public long get_downloadedSize() {
        return _downloadedSize;
    }

    public void set_downloadedSize(long _downloadedSize) {
        this._downloadedSize = _downloadedSize;
    }
    
}
