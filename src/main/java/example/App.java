package example;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
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
    private String handle(List<ToZipObj> toZipObjList, String bucketName) {
        String zipName = "out_put/" + System.currentTimeMillis();
        System.out.println("用于输出的文件名（不带后缀）:" + zipName);
        String tempZipName = "/" + bucketName + "/" + zipName;
        System.out.println("临时文件路径:" + tempZipName);
        convertZipToLocal(bucketName, toZipObjList, tempZipName);
        String objectName = zipName + ".zip";
        System.out.println("文件名:" + objectName);
        return objectName;
    }

    /**
     * 解析请求体
     *
     * @param reqStr
     * @return
     */
    private List<ToZipObj> analysisZipObjList(String reqStr) {
        List<ToZipObj> toZipObjList = new Gson().fromJson(reqStr, new TypeToken<List<ToZipObj>>() {
        }.getType());
        return toZipObjList;
    }

    /**
     * 解析请求体
     */
    private ToZipReq analysisBody(String body) {
        return new Gson().fromJson(body, ToZipReq.class);
    }


    /**
     * 本地生成zip文件（支持空目录）
     */
    private File convertZipToLocal(String bucketName, List<ToZipObj> toZipObjList, String tempZipName) {
        File zipFile = null;
        try {
            System.out.println("开始创建临时文件：" + tempZipName + ".zip");
            zipFile = new File(tempZipName + ".zip");

            FileOutputStream f = new FileOutputStream(zipFile);
            CheckedOutputStream csum = new CheckedOutputStream(f, new Adler32());
            ZipOutputStream zos = new ZipOutputStream(csum);

            byte[] buffer = new byte[1024];

            for (ToZipObj toZipObj : toZipObjList) {
                String path = "/" + bucketName + toZipObj.getFilePath();
                File file = new File(path);
                String zipEntryName = toZipObj.getRoute();
                boolean isDirectory = zipEntryName.endsWith("/");

                if (isDirectory) {
                    // ✅ 关键：处理空目录
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

    /**
     * 解析 request body
     */
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


    public static void main(String[] args) {
        App app = new App();

        // 模拟 bucket（其实就是本地目录）
        String bucketName = "/Users/zhangjie/Downloads/测试打包";
        // 构造测试数据
        List<ToZipObj> list = new ArrayList<>();
        // 普通文件
        ToZipObj file1 = new ToZipObj();
        file1.setFilePath("/Illustrate.docx");
        file1.setRoute("/文件夹/Illustrate.docx");
        list.add(file1);

        // 空目录（关键测试点）
        ToZipObj emptyDir = new ToZipObj();
        emptyDir.setFilePath("/emptyDir/");
        emptyDir.setRoute("/emptyDir/");
        list.add(emptyDir);

        // 输出zip路径（你可以改成你本地路径）
        String tempZipName = "/Users/zhangjie/output/test_zip";

        File zipFile = app.convertZipToLocal(bucketName, list, tempZipName);
        System.out.println("生成完成：" + zipFile.getAbsolutePath());
    }
}
