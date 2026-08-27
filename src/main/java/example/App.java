package example;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.HttpRequestHandler;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import example.model.dto.ToZipObj;
import example.model.dto.ToZipReq;
import example.util.PackingUtil;
import example.util.RedisUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class App implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response, Context context) throws IOException {
        String body = analysisBody(request);
        ToZipReq toZipReq = analysisBody(body);
        List<ToZipObj> toZipFileList = toZipReq.getToZipFileList();
        Boolean success = null;
        String objectName = null;
        String errorMessage = null;
        try {
            objectName = handle(toZipFileList, toZipReq.getBucketName(), toZipReq.getCompressedFileName());
            response.setStatus(200);
            OutputStream out = response.getOutputStream();
            out.write((objectName).getBytes());
            success = true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            e.printStackTrace();
            success = false;
        } finally {
            RedisUtil.send(toZipReq.getKey(), objectName, success, toZipReq.getEnvironment(), errorMessage);
        }
    }

    private static String handle(List<ToZipObj> toZipObjList, String bucketName, String compressedFileName) {
        String zipName;
        if (compressedFileName == null) {
            zipName = "out_put/" + System.currentTimeMillis();
        } else {
            zipName = "out_put/" + compressedFileName;
        }
        System.out.println("用于输出的文件名（不带后缀）:" + zipName);

        String tempZipName = "/" + bucketName + "/" + zipName;
        System.out.println("临时文件路径:" + tempZipName);

        try {
           PackingUtil.compressFileList(bucketName, toZipObjList, tempZipName);
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



    private String analysisBody(HttpServletRequest request) throws IOException {
        BufferedReader br = request.getReader();
        StringBuilder body = new StringBuilder();
        String str;
        while ((str = br.readLine()) != null) {
            body.append(str);
        }
        System.out.println("解析body长度：" + body.length());
        return body.toString();
    }

    /**
     * 本地测试入口
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
        String url = handle(toZipFileList, bucketName, "测试文件夹");
        System.out.println("输出文件：" + url);
        System.out.println("完成,花费：" + watch.elapsed(TimeUnit.MILLISECONDS) + "毫秒");
    }
}