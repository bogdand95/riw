package crawler;

import java.util.Date;

public class Main {

    public static void main(String[] args) {
        Crawler crawler = new Crawler("http://news.ycombinator.com");
        crawler.buildURLList();
        Date start = new Date();
        crawler.downloadPages();
        Date finish = new Date();
        System.out.println("Time: " + (finish.getTime() - start.getTime()) /1000);

    }
}
