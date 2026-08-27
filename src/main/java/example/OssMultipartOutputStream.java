package example;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.UploadPartRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 顺序流式写入 OSS 的 OutputStream。
 * 内部按固定分片缓冲，写满一片立即 uploadPart 上传，finish 时完成分片合并。
 * 内存占用恒定（一个分片大小），本地磁盘零占用，文件大小只受 OSS 限制（单对象 48.8TB）。
 */
public class OssMultipartOutputStream extends OutputStream {

    private final OSS oss;
    private final String bucket;
    private final String key;
    private final byte[] buffer;
    private final List<PartETag> partETags = new ArrayList<>();
    private final String uploadId;

    private int offset = 0;
    private int partNumber = 1;
    private volatile boolean completed = false;
    private volatile boolean aborted = false;

    public OssMultipartOutputStream(OSS oss, String bucket, String key, int partSize) {
        this.oss = oss;
        this.bucket = bucket;
        this.key = key;
        this.buffer = new byte[partSize];
        this.uploadId = oss.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key)).getUploadId();
    }

    @Override
    public void write(int b) throws IOException {
        if (offset == buffer.length) {
            uploadPart();
        }
        buffer[offset++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            if (offset == buffer.length) {
                uploadPart();
            }
            int n = Math.min(len, buffer.length - offset);
            System.arraycopy(b, off, buffer, offset, n);
            offset += n;
            off += n;
            len -= n;
        }
    }

    private void uploadPart() {
        UploadPartRequest req = new UploadPartRequest();
        req.setBucketName(bucket);
        req.setKey(key);
        req.setUploadId(uploadId);
        req.setPartNumber(partNumber++);
        req.setPartSize(offset);
        req.setInputStream(new ByteArrayInputStream(buffer, 0, offset));
        partETags.add(oss.uploadPart(req).getPartETag());
        offset = 0;
    }

    /**
     * 上传剩余缓冲并完成分片合并，幂等。
     */
    public void finish() {
        if (completed || aborted) {
            return;
        }
        // zip 至少有中央目录，一定有数据；partETags 为空说明只写了不足一片
        if (offset > 0 || partETags.isEmpty()) {
            uploadPart();
        }
        oss.completeMultipartUpload(new CompleteMultipartUploadRequest(bucket, key, uploadId, partETags));
        completed = true;
    }

    /**
     * 失败时中止，清理 OSS 侧残留分片，幂等。
     */
    public void abort() {
        if (completed || aborted) {
            return;
        }
        aborted = true;
        try {
            oss.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, uploadId));
        } catch (Exception e) {
            System.out.println("abortMultipartUpload 失败: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        finish();
    }
}
