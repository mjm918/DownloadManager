/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package threads;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import ui.MainView;
import util.HttpReturn;

/**
 *
 * @author julfikar
 */
public class Downloader implements Runnable{
    
    private final String _url;
    private final int _filePartCount;
    private final MainView _progress;
    private final boolean _resume;
    private final String _directory;

    private final Thread thread;
    
    public Downloader(String _url, int _filePartCount, MainView _progress, HashMap<String, String>options){
        this._url = _url;
        this._filePartCount = _filePartCount;
        this._progress = _progress;

        _resume = "y".equals(options.get("resume"));

        String outputDir = options.get("-o");
        _directory = (options.containsKey("o")) ? outputDir : "./";

        thread = new Thread(this,"Main");
    }

    private HttpReturn checkURL(URL url) throws ConnectException{
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("HEAD");
            conn.connect();

            int responseCode = conn.getResponseCode();
            long contentSize = conn.getContentLengthLong();

            HttpReturn result = new HttpReturn(responseCode, contentSize);

            return result;

        } catch (IOException ex) {
            throw new ConnectException(ex.getMessage());
        }
    }

    public ArrayList<FileStream> startDownloadThreads(URL url, long contentSize,
                                                          int partCount, MainView progress) {
        long partSize = contentSize / partCount;
        ArrayList<FileStream> downloadThreadsList = new ArrayList<>(partCount);

        for (int i = 0; i < partCount; i++) {

            long beginByte = i * partSize;
            long endByte;
            if (i == partCount - 1) {
                endByte = contentSize - 1;
            } else {
                endByte = (i + 1) * partSize - 1;
            }

            long currentPartSize = endByte - beginByte + 1;

            FileStream downloadThread = new FileStream(url, beginByte,
                    endByte, currentPartSize, i + 1, progress, _resume);
            downloadThreadsList.add(downloadThread);
            downloadThreadsList.get(i)._startDownloading();
        }

        return downloadThreadsList;
    }

    public void joinDownloadedParts(String fileName, ArrayList<FileStream> downloadParts) throws IOException {
        String outputFile = _directory + fileName;

        try (RandomAccessFile mainFile = new RandomAccessFile(outputFile, "rw")) {
            FileChannel mainChannel = mainFile.getChannel();
            long startPosition = 0;

            for (int i = 0; i < downloadParts.size(); i++) {
                String partName = "." + fileName + ".part" + (i + 1);

                try (RandomAccessFile partFile = new RandomAccessFile(partName, "rw")) {
                    long partSize = downloadParts.get(i).get_downloadedSize();
                    FileChannel partFileChannel = partFile.getChannel();
                    long transferedBytes = mainChannel.transferFrom(partFileChannel,
                            startPosition, partSize);

                    startPosition += transferedBytes;

                    if (transferedBytes != partSize) {
                        throw new RuntimeException("Error joining file! At part: "
                                + (i + 1));
                    }
                }
            }
        }
    }

    public void start(){
        this.thread.start();
    }

    public void join() throws InterruptedException{
        this.thread.join();
    }

    @Override
    public void run() {

        try {
            String fileName = new File(this._url).getName();
            URL url = new URL(this._url);

            HttpReturn result = checkURL(url);
            long contentSize = result._contentLength;
            int responseCode = result._code;

            if (contentSize == -1 || responseCode != 200) {
                String errMessage = "Error while checking URL validity!";
                errMessage += "\nResponse code: " + responseCode;
                errMessage += "\nContent size: " + contentSize;
                throw new RuntimeException(errMessage);
            }

            synchronized (_progress) {
                _progress.httpReturn._contentLength = contentSize;
                _progress.httpReturn._code = responseCode;
                _progress.notifyAll();
            }

            ArrayList<FileStream> downloadParts;

            _progress._thisInstant = Instant.now();

            try {
                downloadParts = startDownloadThreads(url, contentSize,
                        _filePartCount, _progress);
            } catch (RuntimeException ex) {
                throw ex;
            }

            for (int i = 0; i < downloadParts.size(); i++) {
                FileStream currentThread = downloadParts.get(i);
                currentThread._join();
                if (currentThread.get_downloadedSize() != currentThread.get_chunkSize()) {
                    throw new RuntimeException("Download incompleted at part "
                            + (i + 1) + ": " + currentThread.get_downloadedSize());
                }
            }

            synchronized (_progress) {
                _progress._downloadFinished = true;
                _progress.notifyAll();
            }

            joinDownloadedParts(fileName, downloadParts);

            try {
                for (int i = 0; i < downloadParts.size(); i++) {
                    String partName = "." + fileName + ".part" + (i + 1);
                    Path filePath = Paths.get(partName);
                    Files.deleteIfExists(filePath);
                }
            } catch (IOException ex) {
            }

            synchronized (_progress) {
                _progress._chunkDownloadFinished = true;
                _progress.notifyAll();
            }

        } catch (RuntimeException | InterruptedException | IOException ex) {

            synchronized (_progress) {
                _progress.exception = ex;
                _progress.notifyAll();
            }
        }

    }
    
}
