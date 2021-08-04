package net.hwkim.apigw.type;

public class Api {
  private String targetServer;
  private String targetPath;

  public String getTargetServer() {
    return targetServer;
  }
  public String getTargetPath() {
    return targetPath;
  }
 
  public Api() {
  }
  
  public Api(String targetServer, String targetPath) {
    this.targetServer = targetServer;
    this.targetPath = targetPath;
  }
}
