import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.File;
import java.util.ArrayList;

public class LoadScope {
    /**
     *  该函数loadScope()用于加载生成分析域AnalysisScope：scope
     *  @param scopePath：scope.txt配置文件
     *  @param exPath: exclusion.txt配置文件
     *  @param classLoader: 用当前语境下的classloader即可
     *  @param clazz：是加入分析域的类文件代码
     */
    public AnalysisScope loadScope(String scopePath, String exPath, ClassLoader classLoader,
                                   ArrayList<File> clazz) throws InvalidClassFileException {
        AnalysisScope new_scope = null;
        try {
            new_scope = AnalysisScopeReader.readJavaScope(scopePath, new File(exPath), classLoader);
        }catch (Exception e){

        }
        for(int i=0;i<clazz.size();i++) {
            new_scope.addClassFileToScope(ClassLoaderReference.Application, clazz.get(i));
        }
        return new_scope;
    }
}
