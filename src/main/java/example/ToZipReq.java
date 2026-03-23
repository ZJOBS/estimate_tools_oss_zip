package example;

import java.util.List;

public class ToZipReq {

    private String bucketName;

    private List<ToZipObj> toZipFileList;

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public List<ToZipObj> getToZipFileList() {
        return toZipFileList;
    }

    public void setToZipFileList(List<ToZipObj> toZipFileList) {
        this.toZipFileList = toZipFileList;
    }
}
