/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package threads;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.time.Instant;
import ui.MainView;

/**
 *
 * @author julfikar
 */
public class FileStream implements Runnable{
    private Thread _thread;
    private long _startingByte;
    private long _endingByte;
    private long _chunkSize;
    private final boolean _resume;
    private int _chunkCount;
    private URL _url;
    private long _downloadedSize;
    private long _alreadyDownloaded;

    private final String _filename;

    private final MainView progress;
    
    public FileStream(URL _url, long _startingByte, long _endingByte, long _chunkSize, int _chunkCount, MainView progress, boolean _resume){
        this._url = _url;
        this._startingByte = _startingByte;
        this._endingByte = _endingByte;
        this._chunkSize = _chunkSize;
        this._chunkCount = _chunkCount;
        this._resume = _resume;
        this._downloadedSize = 0;
        this._alreadyDownloaded = 0;

        this._filename = "." + (new File(this._url.toExternalForm()).getName() + ".part"
                + this._chunkCount);

        _thread = new Thread(this,"Part#"+this._chunkCount);

        this.progress = progress;

        if(this._resume){
            try (RandomAccessFile partFile = new RandomAccessFile(this._filename, "rw")) {
                this._alreadyDownloaded = partFile.length();
                this._startingByte += this._alreadyDownloaded;
                this._downloadedSize += this._alreadyDownloaded;
            } catch (IOException ex) {

            }
        }
    }

    public void _startDownloading(){
        this._thread.start();
    }

    public void _join() throws InterruptedException{
        this._thread.join();
    }

    public HttpURLConnection _getConnection() throws IOException{
        HttpURLConnection conn = (HttpURLConnection) this._url.openConnection();

        String downloadRange = "bytes=" + this._startingByte + "-" + this._endingByte;
        conn.setRequestProperty("Range", downloadRange);
        conn.connect();

        return conn;
    }

    public void _downloadFile (HttpURLConnection conn) throws IOException{
        InputStream is = conn.getInputStream();

        int chunkSize = (int) Math.pow(2, 13); // 8KB

        try (DataInputStream dataIn = new DataInputStream(is)) {

            long contentLength = conn.getContentLengthLong();
            contentLength += this._alreadyDownloaded;

            byte[] dataArray = new byte[chunkSize];
            int result;

            boolean overwrite = true;
            if (this._resume) {
                overwrite = false;
            }

            synchronized (this.progress) {
                progress._downloadedCounter += this._downloadedSize;
                progress.notifyAll();
            }

            while (this._downloadedSize < contentLength) {
                Instant start = this.progress._thisInstant;
                result = dataIn.read(dataArray, 0, chunkSize);
                Instant stop = Instant.now();
                long time = Duration.between(stop, start).getNano();

                if (result == -1) {
                    break;
                }

                this._downloadedSize += result;
                writeToFile(dataArray, result, overwrite);
                overwrite = false;

                synchronized (this.progress) {
                    this.progress._downloadedCounter += result;
                    this.progress._time += time;
                    this.progress._chunkSizeChanging += result;
                    this.progress._percentage++;

                    this.progress.progress();

                    if (this.progress._percentage == 1) {
                        this.progress._time = 0;
                        this.progress._chunkSizeChanging = 0;
                        this.progress._percentage = 0;
                    }

                    this.progress.notifyAll();
                }
            }
        }
    }

    public void writeToFile(byte[] bytes, int bytesToWrite, boolean overwrite) throws IOException {
        try (FileOutputStream fout = new FileOutputStream(this._filename, !overwrite)) {
            FileChannel outChannel = fout.getChannel();

            ByteBuffer data = ByteBuffer.wrap(bytes, 0, bytesToWrite);

            outChannel.write(data);
        }
    }

    public long get_chunkSize() {
        return _chunkSize;
    }

    public long get_downloadedSize() {
        return _downloadedSize;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection conn = _getConnection();
            _downloadFile(conn);
        } catch (IOException ex) {
            synchronized (this.progress) {
                this.progress.exception = ex;
                this.progress.notifyAll();
            }
        }
    }
    
}
