package com.github;

import com.github.excel.Excel2PDF;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * Hello world!
 */
public class App {

    public static void main(String[] args) throws Exception {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("test.xls");
        String name = UUID.randomUUID().toString().substring(0, 10);
        FileOutputStream outputStream = new FileOutputStream(name + ".pdf");
        Excel2PDF excel2PDF = new Excel2PDF(inputStream, outputStream);
        excel2PDF.convert();
    }

}
