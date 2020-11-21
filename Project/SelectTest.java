import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import jdk.vm.ci.sparc.SPARC;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;

import java.io.*;
import java.util.*;

public class SelectTest {

    //args[0]是judge，如果-m是方法级，如果-c是类级
    //args[1]是target class路径 需要遍历添加到分析域中
    //args[2]是change_info信息

    public static void main(String[] args) {
        function(args);
    }

    public static Set<String> function(String[] args) {

        AnalysisScope scope;
//        Scanner input=new Scanner(System.in);
//        String str=input.next();
//        System.out.println(str);

        //TODO args[0]解析 对目录文件进行遍历 解析文件入口

        String judge=args[0];
        String file_path=args[1];
        String change_path=args[2];
        //存放args[1]中的内容
        ArrayList<String> total_paths=new ArrayList<String>();
        //存放test-classes路径
        ArrayList<String> test_paths=new ArrayList<String>();
        //存放classes路径
        ArrayList<String> class_paths=new ArrayList<String>();
        //存放改变的方法 args[2]中的内容
        ArrayList<String> change_methods=new ArrayList<String>();

        try{
            //target下的文件 添加到对应的arraylist中
            File root=new File(file_path);
            if(root.exists()){
                if(root.listFiles()==null){
                    //nothing to do
                }else{
                    File[] leaves=root.listFiles();
                    for(File leaf:leaves) {
                        if (leaf.getName().equals("test-classes")) {
                            //遍历test-classes目录下的文件 添加入list中（递归）
                            addFile(leaf.listFiles(), test_paths);
                        }
                        if (leaf.getName().equals("classes")) {
                            //遍历classes目录下的文件 添加入class_paths中
                            addFile(leaf.listFiles(), class_paths);
                        }
                    }
                }
            }

            //读取change_info
            BufferedReader bufferedReader=new BufferedReader(new
                    InputStreamReader(new FileInputStream(change_path)));
            String str="";
            while(true){
                str=bufferedReader.readLine();
                if(str==null){
                    break;
                }else {
                    change_methods.add(new String(str));
                }
            }

            //通过judge来判断之后的操作
            /**-----------------------------------------------------
             * -----------------------------------------------------*/
            //方法级的分析
            if(judge.equals("-m")){

                //构建树状数据结构
                Map<Node, HashSet<Node>> graph=new HashMap<>();
                //记录测试的集合
                Set<Node> test_class=new HashSet<>();

                //生成分析域
                LoadScope loadScope=new LoadScope();
                //先构建test_classes
                ArrayList<File> temp_files1=new ArrayList<File>();
                for(int i=0;i<test_paths.size();i++){
                    temp_files1.add(new File(test_paths.get(i)));
                }
                scope=loadScope.loadScope("scope.txt","exclusion.txt",
                        ClassLoader.getSystemClassLoader(),temp_files1);

                //1 生成类层次关系对象
                ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

                //2 生成进入点
                Iterable<Entrypoint> eps= new AllApplicationEntrypoints(scope,cha);

                //3 利用CHA算法构建调用图
                CHACallGraph cg=new CHACallGraph(cha);
                //初始化
                cg.init(eps);

                //4 遍历cg中的所有节点
                for(CGNode node:cg){
                    //node包含类加载器、方法信息等，这里只筛选需要的信息
                    if(node.getMethod() instanceof ShrikeBTMethod){
                        //node.getMethod()返回比较泛化的IMethod实例，不能获取所需信息
                        //一般地，本项目所有业务逻辑相关方法都是ShrikeBTMethod对象
                        ShrikeBTMethod method=(ShrikeBTMethod)node.getMethod();
                        //使用Primordial类加载器加载的类都属于Java原生类，不用考虑
                        if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                            //获取声明该方法的类的内部表示
                            String classInnerName=method.getDeclaringClass().getName().toString();
                            //获取方法签名
                            String signature=method.getSignature();
                            //System.out.println(classInnerName+" "+signature);
                            //通过方法的注解是否含有Test来判断是否为一个测试方法
                            test_class.add(new Node(classInnerName,
                                    signature,node.getMethod().getAnnotations().toString().contains("Test")));
                        }
                    }
                }
                //开始将类文件classes文件加入scope其中
                for(int i=0;i<class_paths.size();i++) {
                    scope.addClassFileToScope(ClassLoaderReference.Application,
                            new File(class_paths.get(i)));
                }

                //开始构建图结构
                //生成类层次关系对象
                ClassHierarchy cha_1=ClassHierarchyFactory.makeWithRoot(scope);
                //进入点
                Iterable<Entrypoint> eps_1=new AllApplicationEntrypoints(scope,cha);
                CHACallGraph cg_1=new CHACallGraph(cha);
                cg_1.init(eps_1);
                for(CGNode node:cg_1){
                    if(node.getMethod() instanceof ShrikeBTMethod){
                        ShrikeBTMethod method=(ShrikeBTMethod)node.getMethod();
                        if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                            //获取声明该方法的类的内部表示
                            String classInnerName=method.getDeclaringClass().getName().toString();
                            //获取方法签名
                            String signature=method.getSignature();
                            //System.out.println(classInnerName+" "+signature);
                            //通过方法的注解是否含有Test来判断是否为一个测试方法
                            Node temp=new Node(classInnerName,signature,node.getMethod()
                                    .getAnnotations().toString().contains("Test"));
                            //如果这个节点不在图中，则填入
                            if(!graph.containsKey(temp)){
                                graph.put(temp,new HashSet<Node>());
                            }
                            //获得当前CGNode的所有前后继关系并加入graph
                            Iterator<CGNode> nodeIterator=cg.getPredNodes(node);
                            while(nodeIterator.hasNext()){
                                CGNode next=nodeIterator.next();
                                if(next.getMethod() instanceof ShrikeBTMethod){
                                    ShrikeBTMethod tm=(ShrikeBTMethod)next.getMethod();
                                    if("Application".equals(tm.getDeclaringClass()
                                    .getName().toString())){
                                        String nextClassInnerName=tm.getDeclaringClass().getName().toString();
                                        String nextSignature=tm.getSignature();
                                        Node next_node=new Node(nextClassInnerName,nextSignature,next.getMethod()
                                        .getAnnotations().toString().contains("Test"));
                                        //连接两个相邻节点
                                        graph.get(temp).add(next_node);
                                    }
                                }
                            }
                        }
                    }
                }

                //构建图完毕
                Map<String,Set<String>> dot=new HashMap<>();
                //生成dot 0表示方法级别 1表示类级
                generateDot(graph,dot,0);

                //受到影响的测试类
                Set<String> influenced=new HashSet<>();
                List<String> temp=new ArrayList<>();
                int k=0;
                while(k<change_methods.size()){
                    //此时是方法级 因此取[1]  否则取[0]
                    temp.add(change_methods.get(k).split(" ")[1]);
                    k++;
                }
                change_methods=new ArrayList<>(temp);
                for(String change:change_methods){
                    //-m表示方法级
                    findRelations(change,graph,test_class,influenced,"-m");
                }

                //对influenced进行操作输出
                BufferedWriter bufferedWriter=null;
                if(judge.equals("-c")){
                    bufferedWriter =new BufferedWriter(new FileWriter("selection-class.txt"));
                }else if(judge.equals("-m")){
                    bufferedWriter =new BufferedWriter(new FileWriter("selection-method.txt"));
                }
                for(String s:influenced){
                    bufferedWriter.write(str+"\n");
                }
                bufferedWriter.close();
                return influenced;
            }

            /**-----------------------------------------------------
             * -----------------------------------------------------*/
            //对类的解析
            else if(judge.equals("-c")){
                //构建树状数据结构
                Map<Node, HashSet<Node>> graph=new HashMap<>();
                //记录测试的集合
                Set<Node> test_class=new HashSet<>();

                //生成分析域
                LoadScope loadScope=new LoadScope();
                //先构建test_classes
                ArrayList<File> temp_files1=new ArrayList<File>();
                for(int i=0;i<test_paths.size();i++){
                    temp_files1.add(new File(test_paths.get(i)));
                }
                scope=loadScope.loadScope("scope.txt","exclusion.txt",
                        ClassLoader.getSystemClassLoader(),temp_files1);

                //1 生成类层次关系对象
                ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

                //2 生成进入点
                Iterable<Entrypoint> eps= new AllApplicationEntrypoints(scope,cha);

                //3 利用CHA算法构建调用图
                CHACallGraph cg=new CHACallGraph(cha);
                //初始化
                cg.init(eps);

                //4 遍历cg中的所有节点
                for(CGNode node:cg){
                    //node包含类加载器、方法信息等，这里只筛选需要的信息
                    if(node.getMethod() instanceof ShrikeBTMethod){
                        //node.getMethod()返回比较泛化的IMethod实例，不能获取所需信息
                        //一般地，本项目所有业务逻辑相关方法都是ShrikeBTMethod对象
                        ShrikeBTMethod method=(ShrikeBTMethod)node.getMethod();
                        //使用Primordial类加载器加载的类都属于Java原生类，不用考虑
                        if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                            //获取声明该方法的类的内部表示
                            String classInnerName=method.getDeclaringClass().getName().toString();
                            //获取方法签名
                            String signature=method.getSignature();
                            //System.out.println(classInnerName+" "+signature);
                            //通过方法的注解是否含有Test来判断是否为一个测试方法
                            test_class.add(new Node(classInnerName,
                                    signature,node.getMethod().getAnnotations().toString().contains("Test")));
                        }
                    }
                }
                //开始将类文件classes文件加入scope其中
                for(int i=0;i<class_paths.size();i++) {
                    scope.addClassFileToScope(ClassLoaderReference.Application,
                            new File(class_paths.get(i)));
                }

                //开始构建图结构
                //生成类层次关系对象
                ClassHierarchy cha_1=ClassHierarchyFactory.makeWithRoot(scope);
                //进入点
                Iterable<Entrypoint> eps_1=new AllApplicationEntrypoints(scope,cha);
                CHACallGraph cg_1=new CHACallGraph(cha);
                cg_1.init(eps_1);
                for(CGNode node:cg_1){
                    if(node.getMethod() instanceof ShrikeBTMethod){
                        ShrikeBTMethod method=(ShrikeBTMethod)node.getMethod();
                        if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                            //获取声明该方法的类的内部表示
                            String classInnerName=method.getDeclaringClass().getName().toString();
                            //获取方法签名
                            String signature=method.getSignature();
                            //System.out.println(classInnerName+" "+signature);
                            //通过方法的注解是否含有Test来判断是否为一个测试方法
                            Node temp=new Node(classInnerName,signature,node.getMethod()
                                    .getAnnotations().toString().contains("Test"));
                            //如果这个节点不在图中，则填入
                            if(!graph.containsKey(temp)){
                                graph.put(temp,new HashSet<Node>());
                            }
                            //获得当前CGNode的所有前后继关系并加入graph
                            Iterator<CGNode> nodeIterator=cg.getPredNodes(node);
                            while(nodeIterator.hasNext()){
                                CGNode next=nodeIterator.next();
                                if(next.getMethod() instanceof ShrikeBTMethod){
                                    ShrikeBTMethod tm=(ShrikeBTMethod)next.getMethod();
                                    if("Application".equals(tm.getDeclaringClass()
                                            .getName().toString())){
                                        String nextClassInnerName=tm.getDeclaringClass().getName().toString();
                                        String nextSignature=tm.getSignature();
                                        Node next_node=new Node(nextClassInnerName,nextSignature,next.getMethod()
                                                .getAnnotations().toString().contains("Test"));
                                        //连接两个相邻节点
                                        graph.get(temp).add(next_node);
                                    }
                                }
                            }
                        }
                    }
                }

                //构建图完毕
                Map<String,Set<String>> dot=new HashMap<>();
                //生成dot 0表示方法级别 1表示类级
                generateDot(graph,dot,1);

                //受到影响的测试类
                Set<String> influenced=new HashSet<>();
                List<String> temp=new ArrayList<>();
                int k=0;
                while(k<change_methods.size()){
                    //此时是方法级 因此取[1]  否则取[0]
                    temp.add(change_methods.get(k).split(" ")[0]);
                    k++;
                }
                change_methods=new ArrayList<>(temp);
                for(String change:change_methods){
                    //-c类级
                    findRelations(change,graph,test_class,influenced,"-c");
                }

                //对influenced进行操作输出
                BufferedWriter bufferedWriter=null;
                if(judge.equals("-c")){
                    bufferedWriter =new BufferedWriter(new FileWriter("selection-class.txt"));
                }else if(judge.equals("-m")){
                    bufferedWriter =new BufferedWriter(new FileWriter("selection-method.txt"));
                }
                for(String s:influenced){
                    bufferedWriter.write(str+"\n");
                }
                bufferedWriter.close();
                return influenced;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;



        //用0-CMD测试使用
//        ArrayList<File> files = new ArrayList<File>();
//        files.add(new File("F:\\大三上\\GPA课程\\自动化测试\\181250188_张越\\AutomatedTesting_181250188\\src\\main\\test\\Data\\0-CMD\\target\\classes\\net\\mooctest\\CMD.class"));
//        files.add(new File("F:\\大三上\\GPA课程\\自动化测试\\181250188_张越\\AutomatedTesting_181250188\\src\\main\\test\\Data\\0-CMD\\target\\test-classes\\net\\mooctest\\CMDTest.class"));
//        files.add(new File("F:\\大三上\\GPA课程\\自动化测试\\181250188_张越\\AutomatedTesting_181250188\\src\\main\\test\\Data\\0-CMD\\target\\test-classes\\net\\mooctest\\CMDTest1.class"));
//        files.add(new File("F:\\大三上\\GPA课程\\自动化测试\\181250188_张越\\AutomatedTesting_181250188\\src\\main\\test\\Data\\0-CMD\\target\\test-classes\\net\\mooctest\\CMDTest2.class"));
//        files.add(new File("F:\\大三上\\GPA课程\\自动化测试\\181250188_张越\\AutomatedTesting_181250188\\src\\main\\test\\Data\\0-CMD\\target\\test-classes\\net\\mooctest\\CMDTest3.class"));

        /**
        //生成分析域
        LoadScope loadScope=new LoadScope();
        scope=loadScope.loadScope("scope.txt","exclusion.txt",
                ClassLoader.getSystemClassLoader(),files);

        //1 生成类层次关系对象
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

        //2 生成进入点
        Iterable<Entrypoint> eps= new AllApplicationEntrypoints(scope,cha);

        //3 利用CHA算法构建调用图
        CHACallGraph cg=new CHACallGraph(cha);
        //初始化
        cg.init(eps);

        //4 遍历cg中的所有节点
        for(CGNode node:cg){
            //node包含类加载器、方法信息等，这里只筛选需要的信息
            if(node.getMethod() instanceof ShrikeBTMethod){
                //node.getMethod()返回比较泛化的IMethod实例，不能获取所需信息
                //一般地，本项目所有业务逻辑相关方法都是ShrikeBTMethod对象
                ShrikeBTMethod method=(ShrikeBTMethod)node.getMethod();
                //使用Primordial类加载器加载的类都属于Java原生类，不用考虑
                if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                    //获取声明该方法的类的内部表示
                    String classInnerName=method.getDeclaringClass().getName().toString();
                    //获取方法签名
                    String signature=method.getSignature();
                    System.out.println(classInnerName+" "+signature);
                }
            }else{
                System.out.println(String.format(
                        "'%s'不是一个ShrikeBTMethod:%s",
                        node.getMethod(),
                        node.getMethod().getClass()));
            }
        }
         */

        /**
        // 循环遍历每一个类
        for(IClass klass : cha) {
            // 打印类名
            System.out.println(klass.getName().toString());
            // 判断当前类是否在zookeeper中
            if(scope.isApplicationLoader(klass.getClassLoader())) {
                // 对在zookeeper中的类的每个函数遍历，并打印函数名
                for (IMethod m : klass.getAllMethods()) {
                    System.out.println(m.getName().toString());
                }
            }
        }
         */
    }

    private static void addFile(File[] fileList, List<String> targetFile){
        int i=0;
        while(i<fileList.length){
            File file=fileList[i];
            if(file==null){
                i++;
                continue;
            }else{
                if(file.isDirectory()){
                    addFile(file.listFiles(),targetFile);
                }else{
                    targetFile.add(file.getPath());
                }
            }
            i++;
        }
    }

    private static void generateDot(Map<Node,HashSet<Node>> graph,Map<String,Set<String>> dot,int judge){
        for(Node node:graph.keySet()){
            String s="";
            if(judge==0){
                s=node.getSignature();
            }else if(judge==1){
                s=node.getClassInnerName();
            }

            if(dot.containsKey("\""+s+"\"")){ }
            else{
                dot.put("\""+s+"\"",new HashSet<String>());
            }

            for (Node next:graph.get(node)){
                String s1="";
                if(judge==0){
                    s1=next.getSignature();
                }else if(judge==1){
                    s1=next.getClassInnerName();
                }
                String temp1="\""+s+"\"";
                String temp2="\""+s1+"\"";
                dot.get(temp1).add(temp2);
            }
        }

        //生成Dot文件 并输出
        try{
            BufferedWriter bufferedWriter;
            if(judge==0){
                bufferedWriter=new BufferedWriter(new FileWriter("src/main/test/Data/2-DataLog/data/method-CMD.dot"));
            }else{
                bufferedWriter=new BufferedWriter(new FileWriter("src/main/test/Data/2-DataLog/data/class-CMD.dot"));
            }
            for(String key:dot.keySet()){
                for(String value:dot.get(key)){
                    String temp=key+ " -> " + value + ";\n";
                    bufferedWriter.write(temp);
                }
            }
            bufferedWriter.close();
            System.out.println("Mission completed");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *  根据变更信息找到关联测试用例加入结果
     *  广度优先算法
     */
    private static void findRelations(String change,Map<Node,HashSet<Node>> graph,Set<Node> test_class,Set<String> influenced,String flag){
        Set<Node> visited =new HashSet<>();
        //用队列的结构记录
        Queue<Node> queue=new LinkedList<>();

        for(Node key:graph.keySet()){
            if(flag.equals("-m")){
                if(key.getSignature().equals(change)){
                    queue.add(key);
                }
            }else if(flag.equals("-c")){
                if(key.getClassInnerName().equals(change)) {
                    queue.add(key);
                }
            }
        }
        while(!queue.isEmpty()){
            for(int i=0;i<queue.size();i++){
                Node node=queue.poll();
                if(visited.contains(node)){
                    continue;
                }
                //未被访问过则填入已访问过的
                visited.add(node);
                if(graph.containsKey(node)){
                    //从总的graph取出这个node
                    Set<Node> temp=graph.get(node);
                    for(Node n:temp){
                        if(visited.contains(n)){
                            continue;
                        }
                        //n入队尾
                        queue.add(n);
                        //判断n节点是否是测试方法 还是生产方法
                        if(flag.equals("-m")){
                            if(test_class.contains(n)&&n.isTest()){
                                influenced.add(n.getAllName());
                            }
                        }else if(flag.equals("-c")){
                            for(Node node1:test_class){
                                if(n.getClassInnerName().equals(node1.getClassInnerName())
                                        && node1.isTest()){
                                    influenced.add(node1.getAllName());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
