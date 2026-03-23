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

public class App implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context) throws IOException {
        String body = analysisBody(request);
        ToZipReq toZipReq = analysisBody(body);
        List<ToZipObj> toZipFileList = toZipReq.getToZipFileList();
        String bucketName = toZipReq.getBucketName();

        String url = handle(toZipFileList, bucketName);

        response.setStatus(200);
        OutputStream out = response.getOutputStream();
        out.write((url).getBytes());
    }

    private static String handle(List<ToZipObj> toZipObjList, String bucketName) {
        String zipName = "out_put/" + System.currentTimeMillis();
        System.out.println("用于输出的文件名（不带后缀）:" + zipName);

        String tempZipName = "/" + bucketName + "/" + zipName;
        System.out.println("临时文件路径:" + tempZipName);

        try {
            compressFileList(bucketName, toZipObjList, tempZipName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String objectName = zipName + ".zip";
        System.out.println("文件名:" + objectName);
        return objectName;
    }

    private static ToZipReq analysisBody(String body) {
        return new Gson().fromJson(body, ToZipReq.class);
    }

    /**
     * ✅ 核心方法（支持空目录）
     */
    private static void compressFileList(String bucketName, List<ToZipObj> toZipObjList, String tempZipName)
            throws IOException, ExecutionException, InterruptedException {

        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("compressFileList-pool-").build();
        ExecutorService executor = new ThreadPoolExecutor(
                5, 10, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20), factory
        );

        ParallelScatterZipCreator parallelScatterZipCreator = new ParallelScatterZipCreator(executor);

        OutputStream outputStream = new FileOutputStream(tempZipName + ".zip");
        ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setEncoding("UTF-8");

        for (ToZipObj toZipObj : toZipObjList) {

            String realFilePath = "/" + bucketName + toZipObj.getFilePath();
            String packagePath = "/" + toZipObj.getRoute();

            // ⭐ 核心：用 route 判断目录
            boolean isDirectory = packagePath.endsWith("/");

            if (isDirectory) {
                // ✅ 空目录（即使本地不存在也能打包）
                ZipArchiveEntry dirEntry = new ZipArchiveEntry(packagePath);
                dirEntry.setMethod(ZipArchiveEntry.STORED);
                dirEntry.setSize(0);
                dirEntry.setUnixMode(UnixStat.DIR_FLAG | 0755);

                parallelScatterZipCreator.addArchiveEntry(
                        dirEntry,
                        () -> new ByteArrayInputStream(new byte[0])
                );

                System.out.println("添加空目录：" + packagePath);
                continue;
            }

            File inFile = new File(realFilePath);

            if (!inFile.exists()) {
                System.out.println("文件不存在，跳过：" + realFilePath);
                continue;
            }

            final InputStreamSupplier inputStreamSupplier = () -> {
                try {
                    return new FileInputStream(inFile);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            };

            ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(packagePath);
            zipArchiveEntry.setMethod(ZipArchiveEntry.DEFLATED);
            zipArchiveEntry.setSize(inFile.length());
            zipArchiveEntry.setUnixMode(UnixStat.FILE_FLAG | 0644);

            parallelScatterZipCreator.addArchiveEntry(zipArchiveEntry, inputStreamSupplier);

            System.out.println("添加文件：" + packagePath);
        }

        parallelScatterZipCreator.writeTo(zipArchiveOutputStream);

        zipArchiveOutputStream.close();
        outputStream.close();
        executor.shutdown();
    }

    private String analysisBody(HttpServletRequest request) throws IOException {
        BufferedReader br = request.getReader();
        StringBuilder body = new StringBuilder();
        String str;
        while ((str = br.readLine()) != null) {
            body.append(str);
        }
        System.out.println("解析body：" + body);
        return body.toString();
    }

    /**
     * ✅ 本地测试入口
     */
    public static void main(String[] args) {
        Stopwatch watch = Stopwatch.createStarted();

        List<ToZipObj> toZipFileList = new ArrayList<ToZipObj>() {{
            // 文件
            add(new ToZipObj() {{
                setFilePath("/Downloads/test/a.txt");
                setRoute("a.txt");
            }});

            // 空目录（不存在也可以）
            add(new ToZipObj() {{
                setFilePath("/Downloads/test/emptyDir");
                setRoute("emptyDir/"); // ⚠️ 必须 /
            }});
        }};

        String bucketName = "Users/zhangjie";

        String url = handle(toZipFileList, bucketName);

        System.out.println("输出文件：" + url);
        System.out.println("完成,花费：" + watch.elapsed(TimeUnit.MILLISECONDS) + "毫秒");
    }
}