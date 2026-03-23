package example;

public class ToZipObj {
    /**
     * 文件路径 例如：http://shenyuanhtfiles.oss-cn-shanghai.aliyuncs.com/多文件夹/11111/测试专用word文件1_20231117174310.doc
     * 应该输入多文件夹/11111/测试专用word文件1_20231117174310.doc
     */
    private String filePath;
    /**
     * 需要导出的包路径（不包含文件名） 例如：/aaa/bbb/ccc.pdf
     */
    private String route;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }
}
