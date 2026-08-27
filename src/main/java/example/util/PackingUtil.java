package example.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import example.ToZipObj;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

public class PackingUtil {

    /**
     * 核心方法（支持空目录）
     */
    public static void compressFileList(String bucketName, List<ToZipObj> toZipObjList, String tempZipName)
            throws IOException, ExecutionException, InterruptedException {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(5, Math.min(cpuCores * 2, 16));
        int queueSize = Math.max(50, poolSize * 3);
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("compressFileList-pool-").build();
        ExecutorService executor = new ThreadPoolExecutor(
                cpuCores, poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), factory
        );

        ParallelScatterZipCreator parallelScatterZipCreator = new ParallelScatterZipCreator(executor);

        OutputStream outputStream = new FileOutputStream(tempZipName + ".zip");
        ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setEncoding("UTF-8");

        try {
            for (ToZipObj toZipObj : toZipObjList) {
                String realFilePath = "/" + bucketName + toZipObj.getFilePath();
                String packagePath = "/" + toZipObj.getRoute();

                boolean isDirectory = packagePath.endsWith("/");

                if (isDirectory) {
                    //  空目录（即使本地不存在也能打包）
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
        } finally {
            zipArchiveOutputStream.close();
            outputStream.close();
            executor.shutdown();
        }
    }
}
