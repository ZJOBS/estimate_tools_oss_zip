package example;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class App implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context) throws IOException {
        String body = analysisBody(request);
        ToZipReq toZipReq = analysisBody(body);
        List<ToZipObj> toZipFileList = toZipReq.getToZipFileList();
        String bucketName = toZipReq.getBucketName();
        //下载并生成压缩包
        String url = handle(toZipFileList, bucketName);
        response.setStatus(200);
        OutputStream out = response.getOutputStream();
        out.write((url).getBytes());
    }


    /**
     * 处理
     *
     * @param toZipObjList
     */
    private static String handle(List<ToZipObj> toZipObjList, String bucketName) {
        String zipName = "out_put/" + System.currentTimeMillis();
        System.out.println("用于输出的文件名（不带后缀）:" + zipName);
        String tempZipName = "/" + bucketName + "/" + zipName;
        System.out.println("临时文件路径:" + tempZipName);
        try {
            compressFileList(bucketName, toZipObjList, tempZipName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        convertZipToLocal(bucketName, toZipObjList, zipName);
        String objectName = zipName + ".zip";
        System.out.println("文件名:" + objectName);
        return objectName;
    }

    private static ToZipReq analysisBody(String body) {
        ToZipReq toZipReq = new Gson().fromJson(body, ToZipReq.class);
        return toZipReq;
    }


    /**
     * 本地生成zip文件（已成功）
     *
     * @param toZipObjList 需要转换的对对象列表
     * @param zipName      生成ZIP的名称
     */
    private static void convertZipToLocal(String bucketName, List<ToZipObj> toZipObjList, String zipName) {
        File zipFile = null;
        try {
            System.out.println("开始创建临时文件：" + zipName + ".zip");
            zipFile = new File(zipName + ".zip");
            FileOutputStream f = new FileOutputStream(zipFile);
            CheckedOutputStream csum = new CheckedOutputStream(f, new Adler32());
            ZipOutputStream zos = new ZipOutputStream(csum);
            for (ToZipObj toZipObj : toZipObjList) {
                String newOssFile = "/" + bucketName + toZipObj.getRoute();
                String realPath = "/" + bucketName + toZipObj.getFilePath();
                System.out.println("读取本地文件：" + realPath);
                FileInputStream inputStream = new FileInputStream(new File(realPath));
                System.out.println("压缩包内文件名：" + newOssFile);
                zos.putNextEntry(new ZipEntry(newOssFile));
                int bytesRead = 0;
                while ((bytesRead = inputStream.read()) != -1) {
                    zos.write(bytesRead);
                }
                inputStream.close();
                zos.closeEntry();
            }
            zos.close();
        } catch (Exception e) {
            System.out.println("异常：" + e.getMessage());
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }


    private static void compressFileList(String bucketName, List<ToZipObj> toZipObjList, String tempZipName) throws IOException, ExecutionException, InterruptedException {
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("compressFileList-pool-").build();
        ExecutorService executor = new ThreadPoolExecutor(5, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(20), factory);
        ParallelScatterZipCreator parallelScatterZipCreator = new ParallelScatterZipCreator(executor);
        OutputStream outputStream = new FileOutputStream(tempZipName + ".zip");
        ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setEncoding("UTF-8");
        for (ToZipObj toZipObj : toZipObjList) {
            String realFilePath = "/" + bucketName + toZipObj.getFilePath();
            String packagePath = "/" + toZipObj.getRoute();
            File inFile = new File(realFilePath);
            final InputStreamSupplier inputStreamSupplier = () -> {
                try {
                    return new FileInputStream(inFile);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return new InputStream() {
                        @Override
                        public int read() throws IOException {
                            return 0;
                        }
                    };
                }
            };
            ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(packagePath);
            zipArchiveEntry.setMethod(ZipArchiveEntry.DEFLATED);
            zipArchiveEntry.setSize(inFile.length());
            zipArchiveEntry.setUnixMode(UnixStat.FILE_FLAG | 436);
            parallelScatterZipCreator.addArchiveEntry(zipArchiveEntry, inputStreamSupplier);
        }
        parallelScatterZipCreator.writeTo(zipArchiveOutputStream);
        zipArchiveOutputStream.close();
        outputStream.close();
    }


    private String analysisBody(HttpServletRequest request) throws IOException {
        BufferedReader br = request.getReader();
        String str, body = "";
        while ((str = br.readLine()) != null) {
            body += str;
        }
        System.out.println("解析body：" + body);
        return body;
    }


    public static void main(String[] args) {
        Stopwatch watch = Stopwatch.createStarted();
        List<ToZipObj> toZipFileList = new ArrayList() {{
            add(new ToZipObj() {{
                setFilePath("/Downloads/大飞机园专业厂房二期项目（一段）单层厂房工程-20241115_20241115200433.gsh6");
                setRoute("大飞机园专业厂房二期项目（一段）单层厂房工程-20241115_20241115200433.gsh6");
            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-02_殷商.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-02_殷商.pdf");
//            }});
//
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-03_西周.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-03_西周.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-04_春秋战国.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-04_春秋战国.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-05_秦汉.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-05_秦汉.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-06_魏晋南北朝.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-06_魏晋南北朝.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-07_隋唐五代.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-07_隋唐五代.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-08_宋代.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-08_宋代.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-09_辽金元.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-09_辽金元.pdf");
//            }});
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-10_明代.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-10_明代.pdf");
//            }});
//
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-11_清代.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说-11_清代.pdf");
//            }});
//
//            add(new ToZipObj() {{
//                setFilePath("/Downloads/台版中国历史图说/台版中国历史图说-12_现代.pdf");
//                setRoute("台版中国历史图说/台版中国历史图说/台版中国历史图说-12_现代.pdf");
//            }});
        }};
        String bucketName = "Users/jiezhang";
        //下载并生成压缩包
        String url = handle(toZipFileList, bucketName);
        System.out.println(url);
        System.out.println("完成,花费：" + watch.elapsed(TimeUnit.MILLISECONDS) + "毫秒");
    }
}
