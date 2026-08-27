package example.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import example.model.dto.ToZipObj;
import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackingUtil {

    /**
     * 核心方法（支持空目录）
     */
    public static void parallelCompressFileList(String bucketName, List<ToZipObj> toZipObjList, String tempZipName)
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


    /**
     * 本地生成zip文件（支持空目录）
     */
    public static File compressFileList(String bucketName, List<ToZipObj> toZipObjList, String tempZipName) {
        File zipFile = null;
        try {
            System.out.println("开始创建临时文件：" + tempZipName + ".zip");
            zipFile = new File(tempZipName + ".zip");

            FileOutputStream f = new FileOutputStream(zipFile);
            CheckedOutputStream csum = new CheckedOutputStream(f, new Adler32());
            ZipOutputStream zos = new ZipOutputStream(csum);

            byte[] buffer = new byte[1024 * 1024];

            for (ToZipObj toZipObj : toZipObjList) {
                String path = "/" + bucketName + toZipObj.getFilePath();
                File file = new File(path);
                String zipEntryName = toZipObj.getRoute();
                boolean isDirectory = zipEntryName.endsWith("/");

                if (isDirectory) {
                    //  关键：处理空目录
                    if (!zipEntryName.endsWith("/")) {
                        zipEntryName += "/";
                    }
                    System.out.println("压缩空目录：" + zipEntryName);
                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    zos.closeEntry();
                } else {
                    System.out.println("读取文件：" + path);
                    FileInputStream inputStream = new FileInputStream(file);
                    System.out.println("压缩文件：" + zipEntryName);
                    zos.putNextEntry(new ZipEntry(zipEntryName));

                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }

                    inputStream.close();
                    zos.closeEntry();
                }
            }

            zos.close();

        } catch (Exception e) {
            System.out.println("异常：" + e.getMessage());
            if (zipFile != null) {
                zipFile.delete();
            }
        }
        return zipFile;
    }
}
