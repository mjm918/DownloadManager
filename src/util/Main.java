/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import threads.Downloader;
import ui.MainView;

/**
 *
 * @author julfikar
 */
public class Main {
    
    public static String mURL;

    private static final String PROGRAM_DIR = System.getenv("HOME")
            + "/DownloadManager";
    private static String DOWNLOADED_LIST_FILENAME = "filelist.csv";

    public Main (String[] args) throws InterruptedException {

        Path path = FileSystems.getDefault().getPath(".");
        DOWNLOADED_LIST_FILENAME = path+File.separator+DOWNLOADED_LIST_FILENAME;
        File file = new File(DOWNLOADED_LIST_FILENAME);
        file.getParentFile().mkdirs();
        file.setReadable(true, false);
        file.setExecutable(true, false);
        file.setWritable(true, false);

        if (args.length == 0) {
            printUsage(args);
            System.exit(0);
        }

        HashMap<String, String> userOptions = new HashMap<>();
        try {
            userOptions = readArgumentOptions(args);
        } catch (RuntimeException ex) {
            printErrorMessage(ex);
        }

        mURL = args[args.length - 1];
        int partsCount = 8;

        HashMap<String, Session> downloadSessionList = null;
        try {
            downloadSessionList = getListOfDownloadedFiles();
        } catch (IOException ex) {
            printErrorMessage(ex);
        }

        String fileName = new File(mURL).getName();
        Session currentDownloadSession;
        currentDownloadSession = checkIfFileWasDownloaded(downloadSessionList, fileName, mURL);

        boolean downloaded = currentDownloadSession._alreadyDownloaded;

        userOptions.put("resume", currentDownloadSession._resume ? "y" : "n");

        if (currentDownloadSession._cancel) {
            return;
        }

        System.out.print("\n");

        if (downloadSessionList == null) {
            downloadSessionList = new HashMap<>();
        }

        if (downloaded) {
            currentDownloadSession = downloadSessionList.get(mURL);
            currentDownloadSession.set_downloadedSize(-1);
        } else {
            currentDownloadSession = new Session(fileName, mURL, -1);
            downloadSessionList.put(mURL, currentDownloadSession);
        }

        try {
            writeInfo(downloadSessionList);
        } catch (IOException ex) {
            printErrorMessage(ex);
        }
        MainView progress = new MainView();

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("--- " + dateFormat.format(date) + " ---\n");
        System.out.println("Downloading from: " + mURL);

        Downloader newDownload = new Downloader(mURL, partsCount, progress, userOptions);

        Instant start = Instant.now();
        newDownload.start();

        System.out.println("Sending HTTP request...");
        synchronized (progress) {
            while (progress.httpReturn._code == 0
                    && progress.exception == null) {
                progress.wait();
            }

            if (progress.exception == null) {

                System.out.println("Response code: "
                        + progress.httpReturn._code);
                System.out.println("Fize size: "
                        + Handler.convertToUOM(progress.httpReturn._contentLength));
            } else {
                printErrorMessage(progress.exception);
            }
        }

        System.out.println();

        Instant downloadFinish = null;

        synchronized (progress) {

            while (!progress._downloadFinished && progress.exception == null) {
                progress.wait();
            }

            if (progress.exception == null) {

                downloadFinish = Instant.now();
                double downloadTime = ((double) (Duration.between(start,
                        downloadFinish).toMillis())) / 1000;

                System.out.println("\n\nTotal download time: " + downloadTime);
            } else {
                printErrorMessage(progress.exception);
            }
        }

        Instant joinFinishedTime;

        synchronized (progress) {
            while (!progress._chunkDownloadFinished && progress.exception == null) {
                progress.wait();
            }

            if (progress.exception == null) {

                joinFinishedTime = Instant.now();
                double joinTime = ((double) (Duration.between(downloadFinish,
                        joinFinishedTime).toMillis())) / 1000;

                System.out.println("Total join time: " + joinTime);
            } else {
                printErrorMessage(progress.exception);
            }
        }
        try {
            newDownload.join();
        } catch (InterruptedException ex) {
            printErrorMessage(ex);
        }

        currentDownloadSession.set_downloadedSize(progress._downloadedCounter);
        try {
            writeInfo(downloadSessionList);
        } catch (IOException ex) {
            printErrorMessage(ex);
        }

        date = new Date();
        System.out.println("Finished downloading!");
        System.out.println("\n--- " + dateFormat.format(date) + " ---");
    }

    private static HashMap<String, String> readArgumentOptions(String[] args) throws RuntimeException {
        ArrayList<String> validOptions = new ArrayList<>(Arrays.asList("-o", "-h", "--help"));
        HashMap<String, String> userOptions = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (validOptions.contains(arg)) {
                String optionValue = null;
                try {
                    optionValue = args[i + 1];
                } catch (ArrayIndexOutOfBoundsException ex) {}

                switch (arg) {
                    case "-o": {
                        try {
                            File filePath = new File(optionValue);

                            if (!filePath.isDirectory()) {
                                String errMessage = "Invalid output file path - "
                                        + optionValue;
                                throw new RuntimeException(errMessage);
                            }
                        } catch (InvalidPathException ex) {
                            String errMessage = "Invalid output file path - "
                                    + optionValue;
                            throw new RuntimeException(errMessage);
                        }

                        userOptions.put("-o", optionValue);
                        i++;
                        break;
                    }
                    case "-h":
                    case "--help": {
                        printUsage(args);
                        System.exit(0);
                    }
                    default:
                        break;
                }
            } else {
                try {
                    URL url = new URL(arg);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.connect();

                    if (i != args.length - 1) {
                        String errMessage = "URL must be at the end!";
                        throw new RuntimeException(errMessage);
                    }
                } catch (IOException ex) {
                    String errMessage = "Invalid option - \"" + arg + "\"";
                    throw new RuntimeException(errMessage);
                }
            }
        }

        return userOptions;
    }

    private static void printUsage(String[] args) {
        System.err.println("\nUsage: java -jar DownloadManager.jar [OPTIONS] URL");
        System.err.println("\nOptions: ");
        System.err.println("\t-o <output directory>\t\tOutput directory for the"
                + " downloaded file (NOT the output file path).");
        System.err.println();
    }

    private static void printErrorMessage(Exception ex) {

        if (ex instanceof MalformedURLException) {
            System.err.println("\nInvalid URL: " + ex.getMessage());
        } else if (ex instanceof ConnectException) {
            ConnectException connectException = (ConnectException) ex;
            System.err.println("\nFailed to connect to the given URL: "
                    + connectException.getMessage());
            System.err.println("\nCheck your internet connection or URL again.");
        } else if (ex instanceof IOException) {
            System.err.println("\nFailed to open the output file: "
                    + ex.getMessage());
        } else if (ex instanceof InterruptedException) {
            System.err.println("\nOne of the thread was interrupted: "
                    + ex.getMessage());
        } else if (ex instanceof RuntimeException) {
            System.err.println(Arrays.toString(ex.getStackTrace()));
        }

        System.err.println("\nExiting!");
        System.exit(0);
    }

    private static HashMap<String, Session> getListOfDownloadedFiles() throws IOException {

        HashMap<String, Session> downloadedList = new HashMap<>();

        try (FileReader downloadedFileList = new FileReader(DOWNLOADED_LIST_FILENAME);
             BufferedReader br = new BufferedReader(downloadedFileList)) {
            String line;

            try {
                while ((line = br.readLine()) != null) {
                    String[] wordList = line.split(",");

                    if (wordList.length == 3) {
                        String fileName = wordList[0];
                        String downloadUrl = wordList[1];
                        long downloadedSize = Long.parseLong(wordList[2]);

                        Session ds = new Session(fileName,
                                downloadUrl, downloadedSize);

                        downloadedList.put(downloadUrl, ds);
                    } else if (wordList.length == 2) {
                        String fileName = wordList[0];
                        String downloadUrl = wordList[1];
                        long downloadedSize = -1;

                        Session ds = new Session(fileName,
                                downloadUrl, downloadedSize);

                        downloadedList.put(downloadUrl, ds);
                    }
                }
            } catch (IOException ex) {
            }
        } catch (FileNotFoundException ex) {
        }

        return downloadedList;
    }

    private static Session checkIfFileWasDownloaded(HashMap<String, Session> downloadSessionList,
                                                            String fileName, String url) {
        Session downloadSession = new Session(fileName, url, -1);
        downloadSession._alreadyDownloaded = false;
        downloadSession._resume = false;

        /*for (Map.Entry<String, Session> entry : downloadSessionList.entrySet()) {
            String currentUrl = entry.getKey();

            if (currentUrl.equals(mURL)) {
                downloadSession._alreadyDownloaded = true;

                Session ds = entry.getValue();
                long downloadedSize = ds.get_downloadedSize();

                if (downloadedSize == -1) {
                    System.out.print("\nYour previous attempt to download"
                            + " from this URL was interrupted. ");
                    System.out.print("Do you want to resume downloading? "
                            + "(y/n) ");

                    char answer = 0;
                    Scanner reader = new Scanner(System.in);
                    while (answer != 'y' && answer != 'Y'
                            && answer != 'n' && answer != 'N') {
                        answer = reader.next().charAt(0);
                    }

                    if (answer == 'y' || answer == 'Y') {
                        downloadSession._resume = true;
                    }

                    break;
                } else {
                    System.out.print("\nYou downloaded from this URL. "
                            + "Do you want to download again? (y/n) ");

                    char answer = 0;
                    Scanner reader = new Scanner(System.in);
                    while (answer != 'y' && answer != 'Y'
                            && answer != 'n' && answer != 'N') {
                        answer = reader.next().charAt(0);
                    }

                    if (answer == 'n' || answer == 'N') {
                        downloadSession._cancel = true;
                    }
                }

                break;
            }
        }*/

        return downloadSession;
    }

    private static void writeInfo(HashMap<String, Session> sessionList) throws IOException {
        try (FileWriter tmpFile = new FileWriter(DOWNLOADED_LIST_FILENAME, false);
             BufferedWriter wr = new BufferedWriter(tmpFile)) {
            for (Map.Entry<String, Session> entry : sessionList.entrySet()) {
                Session session = entry.getValue();
                String url = entry.getKey();
                String fileName = session.get_filename();
                long downloadedSize = session.get_downloadedSize();

                String line = fileName + "," + url + ",";
                line += String.valueOf(downloadedSize) + "\n";

                wr.write(line);
            }
        }
    }
    
}
