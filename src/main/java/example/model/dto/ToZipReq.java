package example.model.dto;

import java.util.List;

public class ToZipReq {

    private String bucketName;

    private String compressedFileName;

    private Long key;

    private String environment;

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

    public String getCompressedFileName() {
        return compressedFileName;
    }

    public void setCompressedFileName(String compressedFileName) {
        this.compressedFileName = compressedFileName;
    }


    public Long getKey() {
        return key;
    }

    public void setKey(Long key) {
        this.key = key;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
