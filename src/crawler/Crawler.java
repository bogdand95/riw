package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.*;

public class Crawler {
    private final String USER_AGENT = "RiwRobot";
    private String urlAddr;
    private Deque<URL> urlList;
    private Set<URL> visitedURL;

    public Crawler(String initialUrl) {
        visitedURL = new HashSet<>();
        urlList = new LinkedList<>();
        try {
            urlAddr = initialUrl;
            urlList.add(new URL(urlAddr));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void buildURLList() {
        try {
            Document doc = Jsoup.connect(urlAddr).get();
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String linkAddr = link.attr("abs:href");
                URL url = new URL(linkAddr);
                if (url.getProtocol().equals("http")) {
                    System.out.println(linkAddr);
                    urlList.add(url);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void buildURLListFromDoc(File file) {
        try {
            Document doc = Jsoup.parse(file, "utf-8");
            Elements links = doc.select("a[href]");
            System.out.println("getting links from " + file.getAbsolutePath());
            for (Element link : links) {
                String linkAddr = link.attr("abs:href");
                if (!linkAddr.equals("")) {
                    URL url = new URL(linkAddr);
                    if (!visitedURL.contains(url) && (url.getProtocol().equals("http"))) {
                        urlList.add(url);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void downloadPages() {
        List<String> disallowed = new ArrayList<>();
        File downloadFolder = new File("downloaded");
        File robotsFile;
        downloadFolder.mkdirs();
        while (!urlList.isEmpty()) {
            URL currentURL = urlList.pollFirst();
            if (!visitedURL.contains(currentURL)) {
                String hostname = currentURL.getHost();
                File hostNameDirectory = new File(downloadFolder, hostname);
                robotsFile = null;
                if (!hostNameDirectory.exists()) {
                    hostNameDirectory.mkdirs();
                    disallowed.clear();
                    robotsFile = getRobotsTxtForCurrentDomain(currentURL, hostNameDirectory);
                }
                if (robotsFile != null) {
                    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(robotsFile))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            if (line.startsWith("User-agent")) {
                                String[] currentLine = line.split(":");
                                if (currentLine.length > 1) {
                                    if (currentLine[1].equals(" *") || currentLine[1].equals(USER_AGENT)) {
                                        while ((line = bufferedReader.readLine()) != null) {
                                            if (line.startsWith("Disallow")) {
                                                String[] disallowedLine = line.split(":");
                                                if (disallowedLine.length > 1)
                                                    disallowed.add(disallowedLine[1]);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        for (String s : disallowed) {
                            System.out.println("Disallowed for " + hostNameDirectory + " " + s);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (disallowed.size() == 0) {
                    buildHttpRequest(currentURL, hostNameDirectory);
                }
                if (!disallowed.contains(" /")) {
                    boolean canDownload = true;
                    for (String item : disallowed) {
                        if (currentURL.getPath().contains(item)) {
                            canDownload = false;
                            break;
                        }
                    }
                    if (canDownload) {
                        System.out.println("CAN DOWNLOAD FROM " + currentURL);
                        buildHttpRequest(currentURL, hostNameDirectory);
                    }
                }
            }
        }
    }

    private File getRobotsTxtForCurrentDomain(URL currentURL, File hostNameDirectory) {
        File file = null;
        HttpURLConnection con = null;
        try {
            URL robotsURL = new URL(currentURL.getProtocol() + "://" + currentURL.getHost() + "/robots.txt");
            System.out.println("REQUESTING robots.txt from  " + robotsURL);
            con = (HttpURLConnection) robotsURL.openConnection();
            con.setRequestProperty("User-Agent", USER_AGENT);
            if (con.getResponseCode() == 404) {
                System.out.println("GOT 404 for " + currentURL.getProtocol() + "://" + currentURL.getHost() + "/robots.txt");
                return null;
            }
            if (con.getResponseCode() == 200) {
                file = new File(hostNameDirectory, "robots.txt");
                try {
                    int readBytes;
                    byte[] buffer = new byte[10240];
                    InputStream responseDataStream = null;
                    FileOutputStream destinationStream = null;
                    try {
                        responseDataStream = con.getInputStream();
                        destinationStream = new FileOutputStream(file);
                        while ((readBytes = responseDataStream.read(buffer)) != -1) {
                            destinationStream.write(buffer, 0, readBytes);
                        }
                    } catch (SocketTimeoutException ex) {
                        System.err.println("Timeout exception " + ex.getMessage());
                    } finally {
                        responseDataStream.close();
                        destinationStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            con.disconnect();
        }
        return file;
    }

    private void buildHttpRequest(URL currentURL, File hostNameDirectory) {
        String path = currentURL.getPath();
        HttpURLConnection httpConn = null;
        try {
            httpConn = (HttpURLConnection) currentURL.openConnection();
            int status = httpConn.getResponseCode();
            boolean redirect = false;
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                System.out.println("REDIRECT FROM " + currentURL);
                redirect = true;
            }

            if (redirect) {
                String newUrlAddress = httpConn.getHeaderField("Location");
                System.out.println("NEW URL: " + newUrlAddress);
                URL newUrl = new URL(newUrlAddress);
                httpConn = (HttpURLConnection) newUrl.openConnection();
                saveFile(newUrl.getPath(), httpConn, hostNameDirectory, newUrl);
            } else {
                saveFile(path, httpConn, hostNameDirectory, currentURL);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        httpConn.disconnect();

    }

    private void saveFile(String path, HttpURLConnection httpConn, File hostNameDirectory, URL currentURL) {
        int status = 0;
        try {
            status = httpConn.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (status == HttpURLConnection.HTTP_OK) {
            // Build file name
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            int lastIndexOfSlash = path.lastIndexOf('/');
            String downloadedFileName = path.substring(lastIndexOfSlash + 1);

            if (path.equals("")) {
                downloadedFileName = "index.html";
            }
            if (!path.endsWith(".html") || !path.endsWith(".html/")) {
                downloadedFileName += ".html";
            }

            // Create new file
            String fullFilePath = path + '/' + downloadedFileName;
            File downloadedFile = new File(hostNameDirectory, fullFilePath);

            // Create parent subdirectories and effective file
            downloadedFile.getParentFile().mkdirs();
            System.out.println("CREATING " + downloadedFile.getAbsolutePath());
            try {
                downloadedFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Write content to file
            try {
                int readBytes;
                byte[] buffer = new byte[10240];
                InputStream responseDataStream = null;
                FileOutputStream destinationStream = null;
                try {
                    responseDataStream = httpConn.getInputStream();
                    destinationStream = new FileOutputStream(downloadedFile);

                    while ((readBytes = responseDataStream.read(buffer)) != -1) {
                        destinationStream.write(buffer, 0, readBytes);
                    }
                } catch (SocketTimeoutException ex) {
                    System.err.println("Timeout exception " + ex.getMessage());
                } finally {
                    responseDataStream.close();
                    destinationStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println(e.getMessage());
            }

            buildURLListFromDoc(downloadedFile);
            visitedURL.add(currentURL);
        } else {

            hostNameDirectory.delete();
        }
    }
}