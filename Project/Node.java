public class Node {

    // 类的内部名称
    private String classInnerName;

    // 方法签名
    private String signature;

    // 记录是否为test方法
    private boolean isTest;

    public Node(String classInnerName,String signature,boolean isTest){
        this.classInnerName=classInnerName;
        this.signature=signature;
        this.isTest=isTest;
    }

    public String getClassInnerName() {
        return classInnerName;
    }

    public String getSignature() {
        return signature;
    }

    public boolean isTest() {
        return isTest;
    }

    public String getAllName(){
        return classInnerName+" "+signature;
    }
}
