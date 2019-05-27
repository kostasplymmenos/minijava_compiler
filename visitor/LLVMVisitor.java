package visitor;
import helpers.ClassData;
import helpers.VarData;
import helpers.MethodData;
import helpers.LineNumberInfo;
import syntaxtree.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

class VarInfo{
    String type;
    String custom_type;
    String id;
    String tempName;
    int offset;

    public VarInfo(String t,String ct,String id,String temp,int offset){
        this.type = t;
        this.custom_type = ct;
        this.id = id;
        this.tempName = temp;
        this.offset = offset;
    }
    public String getTempName(){ return this.tempName; }
    public String getId(){ return this.id; }
    public String getType(){ return this.type; }
    public String getCustomType(){ return this.custom_type; }
    public int getOffset(){ return this.offset; }
}
/**
 * Provides default methods which visit each node in the tree in depth-first
 * order.  Your visitors may extend this class.
 */
public class LLVMVisitor extends GJDepthFirst<String,String> {
    HashMap<String, ClassData> symbol_table;  // class scoped symbol table:  <"class name", "class_data">
    StringBuffer code_buffer;
    String current_class;
    String current_method;
    int label_count;
    int temp_count;
    boolean isExprRetBooleanLiteral;
    boolean isExprRetIntLiteral;
    ArrayList<VarInfo> current_vars;
    ArrayList<VarInfo> current_args;
    ArrayList<VarInfo> current_callParams;
    VarInfo cur_func_return;
    int isFuncCall;
    String cur_Expr_type;
    static final String BOOLEAN_TYPE = "boolean";
    static final String INT_TYPE = "int";
    static final String INT_ARRAY_TYPE = "int[]";
    static final String INTEGER_LITERAL = "int_literal";
    static final String BOOLEAN_LITERAL = "bool_literal";
    static final String CLASS_VAR = "class_var";
    static final String LOCAL_VAR = "local_var";
    static final String FUNC_ARGU = "func_argu";
    static final String TEMP_RES = "temp_res";
    static final String FUNC_RET = "func_ret";

    public LLVMVisitor(HashMap<String, ClassData> table){
        symbol_table = table; // get reference to symbol table
        code_buffer = new StringBuffer();
        current_vars = new ArrayList<VarInfo>();
        current_args = new ArrayList<VarInfo>();
        current_callParams = new ArrayList<VarInfo>();
        cur_func_return = null;
        temp_count = 0;
        label_count = 0;
        isExprRetBooleanLiteral = false;
        isExprRetIntLiteral = false;
        isFuncCall = 0;
        cur_Expr_type = null;
        current_class = "";
        current_method = null;

        String init =
        "declare i8* @calloc(i32, i32)\n"+
        "declare i32 @printf(i8*, ...)\n"+
        "declare void @exit(i32)\n"+
        "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n"+
        "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n"+
        "define void @print_int(i32 %i) {\n"+
        "    %_str = bitcast [4 x i8]* @_cint to i8*\n"+
        "    call i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n"+
        "    ret void\n"+
        "}\n"+
        "define void @print_boolean(i1 %i) {\n"+
        "    %_str = bitcast [4 x i8]* @_cint to i8*\n"+
        "    call i32 (i8*, ...) @printf(i8* %_str, i1 %i)\n"+
        "    ret void\n"+
        "}\n"+
        "define void @throw_oob() {\n"+
        "    %_str = bitcast [15 x i8]* @_cOOB to i8*\n"+
        "    call i32 (i8*, ...) @printf(i8* %_str)\n"+
        "    call void @exit(i32 1)\n"+
        "    ret void\n"+
        "}\n";
        code_buffer.append(init);
    }

    public String getCode(){
        return code_buffer.toString();
    }

    public String getNewLabel(){
        String ret = "label"+label_count;
        label_count++;
        return ret;
    }

    public String getNewTemp(){
        String ret = "%_t"+temp_count;
        temp_count++;
        return ret;
    }

    public void emit(String code){
        code_buffer.append(code);
    }

    public void emitOnTop(String code){
        code_buffer.insert(0,code);
    }

    public int getClassSize(String className){
        int sz = 0;
        for (VarData v : symbol_table.get(className).getVars()) {
            if(v.getType().equals(INT_TYPE)) sz = sz + 4;
            else if(v.getType().equals(BOOLEAN_TYPE)) sz = sz + 1;
            else sz = sz + 8;
        }
        sz += symbol_table.get(className).getMethods().size()*8;
        return sz;
    }

    public int getMethodOffset(String className,String methodName){

        if(symbol_table.get(className).getMethods()!= null && symbol_table.get(className).getMethods().get(methodName) != null) return symbol_table.get(className).getMethods().get(methodName).getOffset();
        String fromClass = className;
        while(symbol_table.get(fromClass).getInheritance() != null){
            if(symbol_table.get(fromClass).getMethods() != null && symbol_table.get(fromClass).getMethods().get(methodName) != null)
                return symbol_table.get(fromClass).getMethods().get(methodName).getOffset();

            fromClass = symbol_table.get(fromClass).getInheritance();
        }
        return -1;
    }

    public VarInfo handleExpressionResult(String expr_res){
        if(isExprRetIntLiteral) return new VarInfo(INT_TYPE,INTEGER_LITERAL,null,expr_res,-1);
        else if(isExprRetBooleanLiteral) return new VarInfo(BOOLEAN_TYPE, BOOLEAN_LITERAL,null,expr_res,-1);
        else if(expr_res.startsWith("%")){
             if(cur_Expr_type.equals(INT_TYPE))
                 return new VarInfo(INT_TYPE,TEMP_RES,null,expr_res,-1);
             else if(cur_Expr_type.equals(BOOLEAN_TYPE))
                 return new VarInfo(BOOLEAN_TYPE,TEMP_RES,null,expr_res,-1);
             else
                 return new VarInfo(cur_Expr_type,TEMP_RES,null,expr_res,-1);

        }
        else{
            VarInfo v = getVarInfoFromIdentifier(expr_res);
            if(v.getCustomType().equals(CLASS_VAR)){
                int offset = findClassVarOffset(v.getId());
                return new VarInfo(v.getType(),CLASS_VAR,expr_res,null,offset);
            }
            else if(v.getCustomType().equals(LOCAL_VAR)){ return v; }
            else if(v.getCustomType().equals(FUNC_ARGU)){ return v; }
        }
        return null;
    }

    public VarInfo handleExpressionResult(VarInfo expr_res){
        return expr_res;
    }

    public VarInfo getVarInfoFromIdentifier(String id){
        for (VarInfo v0 : current_args) { //argu vars
            if(v0.getId().equals(id)) return new VarInfo(v0.getType(),FUNC_ARGU,id,v0.getTempName(),-1);
        }
        for (VarInfo v : current_vars) { //local vars

            if(v.getId().equals(id))return new VarInfo(v.getType(),LOCAL_VAR,id,v.getTempName(),-1);
        }
        //class vars
        for ( VarData vv : symbol_table.get(current_class).getVars()) {
            if(vv.getName().equals(id))
                return new VarInfo(vv.getType(),CLASS_VAR,id,null,vv.getOffset());
        }
        //inhertited vars
        String fromClass = current_class;
        while(symbol_table.get(fromClass).getInheritance() != null){
            for ( VarData v3 : symbol_table.get(fromClass).getVars()) {
                if(v3.getName().equals(id))
                    return new VarInfo(v3.getType(),CLASS_VAR,id,null,v3.getOffset());
            }
            fromClass = symbol_table.get(fromClass).getInheritance();
        }
        return null;
    }

    public int findClassVarOffset(String id){ //TODO inheritance
        for ( VarData vv : symbol_table.get(current_class).getVars()) {
            if(vv.getName().equals(id))
                return vv.getOffset();
        }
        return -1;
    }

    public String loadClassVarPtr(String id){
        emit("; loadClassVarPtr\n");
        int offset = -1;
        String type;
        for ( VarData vv : symbol_table.get(current_class).getVars()) { // TODO maybe id is inherited
            if(vv.getName().equals(id)){
                String elemPtr = getNewTemp();
                emit(elemPtr+" = getelementptr i8, i8* %this, i32 "+(vv.getOffset()+8)+"\n");
                String bitcastT = getNewTemp();
                if(vv.getType().equals(INT_TYPE)){
                    emit(bitcastT+" = bitcast i8* "+elemPtr+" to i32*\n");

                }
                else if(vv.getType().equals(INT_TYPE)){
                    emit(bitcastT+" = bitcast i8* "+elemPtr+" to i1*\n");

                }
                else{ // user defined type is i8*
                    //TODO arrays
                }
                return bitcastT;

            }
        }
        return null;
    }

    public String loadClassVar(String id){
        emit("; loadClassVar\n");
        int offset = -1;
        String type;
        for ( VarData vv : symbol_table.get(current_class).getVars()) { // TODO maybe id is inherited
            if(vv.getName().equals(id)){
                String elemPtr = getNewTemp();
                emit(elemPtr+" = getelementptr i8, i8* %this, i32 "+(vv.getOffset()+8)+"\n");
                String bitcastT = getNewTemp();
                String loadT = getNewTemp();
                if(vv.getType().equals(INT_TYPE)){
                    emit(bitcastT+" = bitcast i8* "+elemPtr+" to i32*\n");
                    emit(loadT+" = load i32, i32* "+bitcastT+"\n");
                }
                else if(vv.getType().equals(INT_TYPE)){
                    emit(bitcastT+" = bitcast i8* "+elemPtr+" to i1*\n");
                    emit(loadT+" = load i1, i1* "+bitcastT+"\n");
                }
                else{ // user defined type
                    emit(loadT+" = load i8, i8* "+bitcastT+"\n"); //TODO
                }
                return loadT;

            }
        }
        return null;
    }
    public String handleAssignment(String id,String expr_ret){ //TODO may optimize this - load/store
        emit("; "+id+" = "+expr_ret+"\n");
        emit("; handleAssignment\n");
        VarInfo v_id = getVarInfoFromIdentifier(id);
        VarInfo v_exp_res = handleExpressionResult(expr_ret);
        if(v_id.getCustomType().equals(LOCAL_VAR)){ //store expr result to local var
            if(v_exp_res.getCustomType().equals(LOCAL_VAR) || v_exp_res.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v_id.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v_exp_res.getTempName()+"\n");
                    emit("store i32 "+loadLocal2+", i32* "+v_id.getTempName()+"\n");
                }
                else if(v_id.getType().equals(BOOLEAN_TYPE)){
                    emit(loadLocal2+" = load i1, i1* "+v_exp_res.getTempName()+"\n");
                    emit("store i1 "+loadLocal2+", i1* "+v_id.getTempName()+"\n");
                }
                else{ //user-defined type
                    emit(loadLocal2+" = load i8*, i8** "+v_exp_res.getTempName()+"\n");
                    emit("store i8* "+loadLocal2+", i8** "+v_id.getTempName()+"\n");
                }
            }
            else if(v_exp_res.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v_id.getId());//TODO
                if(v_id.getType().equals(INT_TYPE))
                      emit("store i32 "+loadedVar2+", i32* "+v_id.getTempName()+"\n");
                else if(v_id.getType().equals(BOOLEAN_TYPE))
                      emit("store i1 "+loadedVar2+", i1* "+v_id.getTempName()+"\n");
                else{ //user-defined type
                      emit("store i8* "+loadedVar2+", i8** "+v_id.getTempName()+"\n");
                }
            }
            else{ //TODO
                if(v_id.getType().equals(INT_TYPE)) emit("store i32 "+v_exp_res.getTempName()+", i32* "+v_id.getTempName()+"\n");
                else if(v_id.getType().equals(BOOLEAN_TYPE)) emit("store i1 "+v_exp_res.getTempName()+", i1* "+v_id.getTempName()+"\n");
                else emit("store i8* "+v_exp_res.getTempName()+", i8** "+v_id.getTempName()+"\n");
            }
        }
        else if(v_id.getCustomType().equals(CLASS_VAR)){ //store expr result to class var //TODO other types
            String loadedVarPtr = loadClassVarPtr(v_id.getId());

            if(v_exp_res.getCustomType().equals(LOCAL_VAR) || v_exp_res.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v_id.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v_exp_res.getTempName()+"\n");
                    emit("store i32 "+loadLocal2+", i32* "+loadedVarPtr+"\n");
                }
                else if(v_id.getType().equals(BOOLEAN_TYPE)){
                    emit(loadLocal2+" = load i1, i1* "+v_exp_res.getTempName()+"\n");
                    emit("store i1 "+loadLocal2+", i1* "+loadedVarPtr+"\n");
                }
                else{ //user-defined type
                    emit(loadLocal2+" = load i8*, i8** "+v_exp_res.getTempName()+"\n");
                    emit("store i8* "+loadLocal2+", i8** "+loadedVarPtr+"\n");
                }
            }
            else if(v_exp_res.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v_id.getId());
                if(v_id.getType().equals(INT_TYPE))
                      emit("store i32 "+loadedVar2+", i32* "+loadedVarPtr+"\n");
                else if(v_id.getType().equals(BOOLEAN_TYPE))
                      emit("store i1 "+loadedVar2+", i1* "+loadedVarPtr+"\n");
                else //user-defined type
                      emit("store i8* "+loadedVar2+", i8** "+loadedVarPtr+"\n");
            }
            else{ //TODO
                if(v_id.getType().equals(INT_TYPE)) emit("store i32 "+v_exp_res.getTempName()+", i32* "+loadedVarPtr+"\n");
                else if(v_id.getType().equals(BOOLEAN_TYPE)) emit("store i1 "+v_exp_res.getTempName()+", i1* "+loadedVarPtr+"\n");
                else emit("store i8* "+v_exp_res.getTempName()+", i8** "+loadedVarPtr+"\n");
            }
        }
        return null;
    }


    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String extra) {
       String _ret=null;
       emit("define i32 @main() {\n");
       n.f0.accept(this, null);
       String className = n.f1.accept(this, null);
       current_class = className;
       current_method = "main";
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       n.f4.accept(this, null);
       n.f5.accept(this, null);
       n.f6.accept(this, null);
       n.f7.accept(this, null);
       n.f8.accept(this, null);
       n.f9.accept(this, null);
       n.f10.accept(this, null);
       n.f11.accept(this, null);
       n.f12.accept(this, null);
       n.f13.accept(this, null);
       n.f14.accept(this, "local");
       n.f15.accept(this, null);
       n.f16.accept(this, null);
       current_vars.clear();
       n.f17.accept(this, null);
       emit("ret i32 0\n}\n\n");
       emitOnTop("@."+className+"_vtable = global [0 x i8*] []\n");
       return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String extra) {
       String _ret=null;
       String vtableCode;
       n.f0.accept(this, null);
       String className = n.f1.accept(this, null);
       current_class = className;
       int class_method_count = symbol_table.get(className).getMethods().size();
       vtableCode = "@."+className+"_vtable = global ["+class_method_count+" x i8*] [";

       int mcount = 0;
       for (MethodData m : symbol_table.get(className).getMethods().values()) {
           mcount++;
           vtableCode += "i8* bitcast (";
           vtableCode += m.getType().equals(INT_TYPE) ? " i32 (i8*" : " i1 (i8*";
           for (VarData arg : m.getArgs()) {
               vtableCode += arg.getType().equals(INT_TYPE) ? ", i32" : ", i1";
           }
           if(mcount == symbol_table.get(className).getMethods().size())
                vtableCode += ")* @"+className+"."+m.getName()+" to i8*)] \n";
            else
                vtableCode += ")* @"+className+"."+m.getName()+" to i8*), ";
       }
       n.f2.accept(this, null);
       n.f3.accept(this, "heap");
       n.f4.accept(this, null);
       n.f5.accept(this, null);
       emitOnTop(vtableCode);
       return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String extra) {
       String _ret=null;
       n.f0.accept(this, null);
       String className = n.f1.accept(this, null);
       current_class = className;
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       n.f4.accept(this, null);
       n.f5.accept(this, "heap");
       n.f6.accept(this, null);
       n.f7.accept(this, null);
       return _ret;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    public String visit(MethodDeclaration n, String extra){
       String _ret=null;
       String code = "";
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       String methodName = n.f2.accept(this, null);
       current_method = methodName;
       MethodData m = symbol_table.get(current_class).getMethods().get(current_method);
       code += m.getType().equals(INT_TYPE) ? "define i32 " : "define i1 ";
       code += "@"+current_class+"."+methodName+"(i8* %this ";
       for (VarData arg : m.getArgs()) {
           code += ", ";
           code += arg.getType().equals(INT_TYPE) ? "i32 %" : "i1 %";
           code += arg.getName();
       }
       code += ") {\n";
       for (VarData arg : m.getArgs()) {
           String argTemp = getNewTemp();
           current_args.add(new VarInfo(arg.getType(),FUNC_ARGU,arg.getName(),argTemp,-1));
           code += argTemp;
           code += arg.getType().equals(INT_TYPE) ? " = alloca i32\n" : "alloca i1\n";
           code += arg.getType().equals(INT_TYPE) ? "store i32 %"+arg.getName()+", i32* "+argTemp+"\n" : "store i1 %"+arg.getName()+", i1* "+argTemp+"\n";
       }
       emit(code);
       n.f3.accept(this, null);
       n.f4.accept(this, null);

       n.f5.accept(this, null);
       n.f6.accept(this, null);
       n.f7.accept(this, "local");

       n.f8.accept(this, null);
       n.f9.accept(this, null);
       String expr_res = n.f10.accept(this, null);
       VarInfo vfound = handleExpressionResult(expr_res);
       if(vfound.getCustomType().equals(INTEGER_LITERAL))  emit("ret i32 "+vfound.getTempName()+"\n");
       else if(vfound.getCustomType().equals(BOOLEAN_LITERAL)) emit("ret i1 "+vfound.getTempName()+"\n");
       else if(vfound.getCustomType().equals(TEMP_RES)){

            if(vfound.getType().equals(INT_TYPE)) emit("ret i32 "+vfound.getTempName()+"\n");
            else if(vfound.getType().equals(BOOLEAN_TYPE)) emit("ret i1 "+vfound.getTempName()+"\n");
       }
       else if(vfound.getCustomType().equals(LOCAL_VAR)){
           String loadT = getNewTemp();
           if(vfound.getType().equals(INT_TYPE)){ emit(loadT+" = load i32, i32* "+vfound.getTempName()+"\n"); emit("ret i32 "+loadT+"\n"); }
           else if(vfound.getType().equals(BOOLEAN_TYPE)){ emit(loadT+" = load i1, i1* "+vfound.getTempName()+"\n"); emit("ret i1 "+loadT+"\n"); }
       }
       else if(vfound.getCustomType().equals(CLASS_VAR)){

           if(vfound.getType().equals(INT_TYPE)){
               String elemPtr = getNewTemp();
                emit(elemPtr+" = getelementptr i8, i8* %this, i32 "+(vfound.getOffset()+8)+"\n");
                String castAddress = getNewTemp();
                emit(castAddress+" = bitcast i8* "+elemPtr+" to i32*\n");
                String loadT2 = getNewTemp();
                emit(loadT2+" = load i32, i32* "+castAddress+"\n");
                emit("ret i32 "+loadT2+"\n");
            }
           else if(vfound.getType().equals(INT_TYPE)){
               String elemPtr = getNewTemp();
                emit(elemPtr+" = getelementptr i8, i8* %this, i1 "+(vfound.getOffset()+8)+"\n");
                String castAddress = getNewTemp();
                emit(castAddress+" = bitcast i1* "+elemPtr+" to i1*\n");
                String loadT2 = getNewTemp();
                emit(loadT2+" = load i1, i1* "+castAddress+"\n");
                emit("ret i1 "+loadT2+"\n");
            }
       }
       else if(vfound.getCustomType().equals(FUNC_ARGU)){
           String loadArg = getNewTemp();
           VarInfo varg = getVarInfoFromIdentifier(expr_res);

           if(vfound.getType().equals(INT_TYPE)){
               emit(loadArg+" = load i32, i32* "+varg.getTempName()+"\n");
               emit("ret i32 "+loadArg+"\n");
           }
           else if(vfound.getType().equals(BOOLEAN_TYPE)){
               emit(loadArg+" = load i1, i1* "+varg.getTempName()+"\n");
               emit("ret i1 "+loadArg+"\n");
           }
       }
       n.f11.accept(this, null);
       n.f12.accept(this, null);

       emit("}\n");
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n, String extra){
       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, String extra){
        if(extra.equals("local")){
            String temp = getNewTemp();
            String type = n.f0.accept(this, null);
            if(type.equals(INT_TYPE))
                emit(temp+" = alloca i32\n");
            else if(type.equals(BOOLEAN_TYPE))
                emit(temp+" = alloca i1\n");
            else if(type.equals(INT_ARRAY_TYPE))
                emit(temp+" = alloca i32\n"); //TODO
            else
                emit(temp+" = alloca i8*\n");
            String id = n.f1.accept(this, null);
            n.f2.accept(this, null);
            current_vars.add(new VarInfo(type,LOCAL_VAR, id, temp,-1));
        }
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String extra){
       String _ret=null;
       String id = n.f0.accept(this, null);
       n.f1.accept(this, null);
       String expr_ret = n.f2.accept(this, null);
       handleAssignment(id,expr_ret); // TODO
       n.f3.accept(this, null);
       return _ret;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String extra){
       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       n.f2.accept(this, null);

       n.f3.accept(this, null);
       n.f4.accept(this, null);
       n.f5.accept(this, null);

       n.f6.accept(this, null);
       return INT_ARRAY_TYPE;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String extra) {
        emit("; IfStatement\n");
       String _ret=null;
       n.f0.accept(this, null);
       String if_label = getNewLabel();
       String else_label = getNewLabel();
       String out_label = getNewLabel();
       n.f1.accept(this, null);
       String temp = n.f2.accept(this, null); //TODO is there any other except compare?
       emit("br i1 "+ temp+ ", label %"+if_label+", label %"+else_label+"\n");
       emit(if_label+":\n");

       n.f3.accept(this, null);
       n.f4.accept(this, null);
       n.f5.accept(this, null);
       emit("br label %"+out_label+"\n");
       emit(else_label+":\n");
       n.f6.accept(this, null);
       emit("br label %"+out_label+"\n");
       emit(out_label+":\n");
       return _ret;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String extra) {
        emit("; WhileStatement\n");
       String _ret=null;
       String cond_label = getNewLabel();
       String in_label = getNewLabel();
       String out_label = getNewLabel();
       emit("br label %"+cond_label+"\n");
       emit(cond_label+":\n");

       n.f0.accept(this, null);
       n.f1.accept(this, null);
       String temp = n.f2.accept(this, null);
       emit("br i1 "+ temp+ ", label %"+in_label+", label %"+out_label+"\n");
       emit(in_label+":\n");
       n.f3.accept(this, null);
       n.f4.accept(this, null);
       emit("br label %"+cond_label+"\n");
       emit(out_label+":\n");
       return _ret;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String extra){
        emit("; PrintStatement\n");
       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       String expr = n.f2.accept(this, null);
       VarInfo v = handleExpressionResult(expr);

       if(v.getCustomType().equals(LOCAL_VAR) || v.getCustomType().equals(FUNC_ARGU)){
           String loadT = getNewTemp();
           if(v.getType().equals(INT_TYPE)){
               emit(loadT+" = load i32, i32* "+v.getTempName()+"\n");
                emit("call void (i32) @print_int(i32 "+ loadT +")\n");
            }
           else if(v.getType().equals(BOOLEAN_TYPE)){
               emit(loadT+" = load i1, i1* "+v.getTempName()+"\n");
               emit("call void (i32) @print_boolean(i1 "+ loadT +")\n");
           }
       }
       else if(v.getCustomType().equals(CLASS_VAR)){
           String loadT = loadClassVar(v.getId());
           if(v.getType().equals(INT_TYPE)){
               emit("call void (i32) @print_int(i32 "+ loadT +")\n");
            }
           else if(v.getType().equals(INT_TYPE)){
               emit("call void (i32) @print_boolean(i1 "+ loadT +")\n");
            }
       }
       else{
           if(v.getType().equals(INT_TYPE)) emit("call void (i32) @print_int(i32 "+ v.getTempName() +")\n");
           else if(v.getType().equals(BOOLEAN_TYPE)) emit("call void (i32) @print_boolean(i1 "+ v.getTempName() +")\n");
           else emit("call void (i32) @print_int(i32 "+ v.getTempName() +")\n"); //TODO func ret
       }

       n.f3.accept(this, null);
       n.f4.accept(this, null);
       return _ret;
    }

    /**
     * f0 -> AndExpression()
     *       | CompareExpression()
     *       | PlusExpression()
     *       | MinusExpression()
     *       | TimesExpression()
     *       | ArrayLookup()
     *       | ArrayLength()
     *       | MessageSend()
     *       | Clause()
     */
    public String visit(Expression n, String extra) {
        String result;
        if(isFuncCall == 1){
            result = n.f0.accept(this, null);
            //TODO maybe handle expression
            if(this.isExprRetBooleanLiteral == true){
                current_callParams.add(new VarInfo(BOOLEAN_TYPE,BOOLEAN_LITERAL,null,result,-1));
                return null;
            }

            if(this.isExprRetIntLiteral == true){
                current_callParams.add(new VarInfo(INT_TYPE,INTEGER_LITERAL,null,result,-1));
                return null;
            }
            if(result.startsWith("%")){
                if(cur_Expr_type.equals(INT_TYPE)) current_callParams.add(new VarInfo(INT_TYPE,TEMP_RES,null,result,-1));
                else if(cur_Expr_type.equals(BOOLEAN_TYPE)) current_callParams.add(new VarInfo(BOOLEAN_TYPE,TEMP_RES,null,result,-1));
                return result;
            }
            else{
                VarInfo vfound = getVarInfoFromIdentifier(result);
                current_callParams.add(new VarInfo(vfound.getType(),vfound.getCustomType(),result,null,vfound.getOffset())); //identifier
                return null;
            }

        }
       return n.f0.accept(this, null);
    }

    /**
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */
    public String visit(PrimaryExpression n, String extra) {
        this.isExprRetBooleanLiteral = false;
        this.isExprRetIntLiteral = false;
       return n.f0.accept(this, null);
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, String extra){
        cur_Expr_type = BOOLEAN_TYPE;
        String _ret=null;
        n.f0.accept(this, null);
        n.f1.accept(this, null);
        n.f2.accept(this, null);

        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String extra){
        emit("; CompareExpression\n");
        String id1 = n.f0.accept(this, null);
        n.f1.accept(this, null);
        VarInfo v1 = handleExpressionResult(id1);
        String id2 = n.f2.accept(this, null);
        VarInfo v2 = handleExpressionResult(id2);
        cur_Expr_type = BOOLEAN_TYPE;
        System.out.println("compare: "+id1+" < "+id2+" -> "+v1.getCustomType()+" / "+v2.getCustomType());
        String ret_temp = getNewTemp();
        if(v1.getCustomType().equals(LOCAL_VAR) || v1.getCustomType().equals(FUNC_ARGU) ){

            String loadLocal = getNewTemp();
            emit(loadLocal+" = load i32, i32* "+v1.getTempName()+"\n");
            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = icmp slt i32 "+loadLocal+", "+loadLocal2+"\n");
                }
            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());//TODO
                if(v2.getType().equals(INT_TYPE))
                    emit(ret_temp+" = icmp slt i32 "+loadLocal+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE))  emit(ret_temp+" = icmp slt i32 "+loadLocal+", "+v2.getTempName()+"\n");
        }
        else if(v1.getCustomType().equals(CLASS_VAR)){ //store expr result to class var //TODO other types
            String loadedVar = loadClassVar(v1.getId());

            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = icmp slt i32 "+loadedVar+", "+loadLocal2+"\n");
                }

            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());
                if(v2.getType().equals(INT_TYPE))
                      emit(ret_temp+" = icmp slt i32 "+loadedVar+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE)) emit(ret_temp+" = icmp slt i32 "+loadedVar+", "+v2.getTempName()+"\n");
        }
        else{
            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = icmp slt i32 "+v1.getTempName()+", "+loadLocal2+"\n");
                }

            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());
                if(v2.getType().equals(INT_TYPE))
                      emit(ret_temp+" = icmp slt i32 "+v1.getTempName()+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE)) emit(ret_temp+" = icmp slt i32 "+v1.getTempName()+", "+v2.getTempName()+"\n");
        }
        return ret_temp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String extra){
        emit("; PlusExpression\n");
        String id1 = n.f0.accept(this, null);
        n.f1.accept(this, null);
        VarInfo v1 = handleExpressionResult(id1);
        String id2 = n.f2.accept(this, null);
        VarInfo v2 = handleExpressionResult(id2);
        cur_Expr_type = BOOLEAN_TYPE;
        System.out.println("add: "+id1+" + "+id2+" -> "+v1.getCustomType()+" / "+v2.getCustomType());
        String ret_temp = getNewTemp();
        if(v1.getCustomType().equals(LOCAL_VAR) || v1.getCustomType().equals(FUNC_ARGU) ){

            String loadLocal = getNewTemp();
            emit(loadLocal+" = load i32, i32* "+v1.getTempName()+"\n");
            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = add i32 "+loadLocal+", "+loadLocal2+"\n");
                }
            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());//TODO
                if(v2.getType().equals(INT_TYPE))
                    emit(ret_temp+" = add i32 "+loadLocal+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE))  emit(ret_temp+" = add i32 "+loadLocal+", "+v2.getTempName()+"\n");
        }
        else if(v1.getCustomType().equals(CLASS_VAR)){ //store expr result to class var //TODO other types
            String loadedVar = loadClassVar(v1.getId());

            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = add i32 "+loadedVar+", "+loadLocal2+"\n");
                }

            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());
                if(v2.getType().equals(INT_TYPE))
                      emit(ret_temp+" = add i32 "+loadedVar+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE)) emit(ret_temp+" = add i32 "+loadedVar+", "+v2.getTempName()+"\n");
        }
        else{
            if(v2.getCustomType().equals(LOCAL_VAR) || v2.getCustomType().equals(FUNC_ARGU)){
                String loadLocal2 = getNewTemp();
                if(v2.getType().equals(INT_TYPE)){
                    emit(loadLocal2+" = load i32, i32* "+v2.getTempName()+"\n");
                    emit(ret_temp+" = add i32 "+v1.getTempName()+", "+loadLocal2+"\n");
                }

            }
            else if(v2.getCustomType().equals(CLASS_VAR)){
                String loadedVar2 = loadClassVar(v2.getId());
                if(v2.getType().equals(INT_TYPE))
                      emit(ret_temp+" = add i32 "+v1.getTempName()+", "+loadedVar2+"\n");
            }
            else
                if(v2.getType().equals(INT_TYPE)) emit(ret_temp+" = add i32 "+v1.getTempName()+", "+v2.getTempName()+"\n");
        }
        return ret_temp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String extra){
        cur_Expr_type = INT_TYPE;
        String _ret=null;
        String newTemp = getNewTemp();
        String toEmit = newTemp + " = sub i32 ";

        String id1 = n.f0.accept(this, null);
        if(this.isExprRetIntLiteral)
             toEmit += id1+", ";
        else{
            for (VarInfo v : current_vars) { // if primary expr is local var
                if(v.getId().equals(id1)){
                    String loadT = getNewTemp();
                    emit(loadT+" = load i32, i32* "+v.getTempName() +"\n");
                    toEmit += loadT + ", "; //TODO change for bracket expr
                }
            }
        }
        n.f1.accept(this, null);
        String id2 = n.f2.accept(this, null);

        if(this.isExprRetIntLiteral)
             toEmit += id2+"\n";
        else{
            for (VarInfo v : current_vars) {
                if(v.getId().equals(id2)){
                    String loadT = getNewTemp();
                    emit(loadT+" = load i32, i32* "+v.getTempName()+"\n");
                    toEmit += loadT + "\n";
                }

            }
        }
        emit(toEmit);
        return newTemp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String extra){
        cur_Expr_type = INT_TYPE;
        String _ret=null;
        String newTemp = getNewTemp();
        String toEmit = newTemp + " = mul i32 ";

        String id1 = n.f0.accept(this, null);
        if(this.isExprRetIntLiteral)
             toEmit += id1+", ";
        else{
            for (VarInfo v : current_vars) { // if primary expr is local var
                if(v.getId().equals(id1)){
                    String loadT = getNewTemp();
                    emit(loadT+" = load i32, i32* "+v.getTempName() +"\n");
                    toEmit += loadT + ", "; //TODO change for bracket expr
                }
            }
        }
        n.f1.accept(this, null);
        String id2 = n.f2.accept(this, null);

        if(this.isExprRetIntLiteral)
             toEmit += id2+"\n";
        else{
            for (VarInfo v : current_vars) {
                if(v.getId().equals(id2)){
                    String loadT = getNewTemp();
                    emit(loadT+" = load i32, i32* "+v.getTempName()+"\n");
                    toEmit += loadT + "\n";
                }

            }
        }
        emit(toEmit);
        return newTemp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String extra){
        //TODO cur_Expr_type
        String _ret=null;
       n.f0.accept(this, null);

       n.f1.accept(this, null);
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       return INT_TYPE;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String extra){
        cur_Expr_type = INT_TYPE;
       String _ret=null;
      n.f0.accept(this, null);

       n.f1.accept(this, null);
       n.f2.accept(this, null);
       return INT_TYPE;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String extra) { //TODO hanlde other types
        emit("; MessageSend\n");
        cur_func_return = null;
       String _ret=null;
       String code = "";
       isFuncCall = 1;
       String obj = n.f0.accept(this, null);
       n.f1.accept(this, null);
       String mName = n.f2.accept(this, null);
       n.f3.accept(this, null);
       VarInfo v0 = handleExpressionResult(obj);
       int method_vtable_offset = getMethodOffset(v0.getType(),mName) / 8;
       String castAddress = getNewTemp();

       String loadLocal = getNewTemp();
       if(v0.getCustomType().equals(LOCAL_VAR)){

            code += loadLocal+" = load i8*, i8** "+v0.getTempName()+"\n";
            code += castAddress+" = bitcast i8* "+loadLocal+" to i8***\n";
       }
       else if(v0.getCustomType().equals(LOCAL_VAR)){
           //TODO
       }
       String loadT = getNewTemp();
       code += loadT+" = load i8**, i8*** "+castAddress+"\n";
       String vtable0 = getNewTemp();
       code += vtable0+" = getelementptr i8*, i8** "+loadT+", i32 "+method_vtable_offset+"\n";
       String loadFunc = getNewTemp();
       code += loadFunc+" = load i8*, i8** "+vtable0+"\n";

       MethodData m = symbol_table.get(cur_Expr_type).getMethods().get(mName);

       n.f4.accept(this, null);
       isFuncCall = 0;
       n.f5.accept(this, null);
       String funcT = getNewTemp();
       code += funcT+" = bitcast i8* "+loadFunc+" to ";
       code += m.getType().equals(INT_TYPE) ? "i32 (i8* " : "i1 (i8* ";
       for(int i = 0; i < m.getArgs().size(); i++){
           code += m.getArgs().get(i).getType().equals(INT_TYPE) ? ", i32" : ", i1";
       }
       code+=" )*\n";
       String callT = getNewTemp();
       code += callT+" = call ";
       code += m.getType().equals(INT_TYPE) ? "i32 "+funcT : "i1 "+funcT;
       code += "(i8* "+loadLocal;
       for(int i = 0; i < m.getArgs().size(); i++){
           code += m.getArgs().get(i).getType().equals(INT_TYPE) ? ", i32 " : ", i1 ";
           if(current_callParams.get(i).getId() != null){
               VarInfo v = getVarInfoFromIdentifier(current_callParams.get(i).getId());
               String loadT2 = getNewTemp();
               String toEmit;
               emit(loadT2);
               if(v.getType().equals(INT_TYPE)) toEmit = " = load i32, i32* "+v.getTempName()+"\n";
               else toEmit = " = load i1, i1* "+v.getTempName()+"\n";
               emit(toEmit);
               code += loadT2;
           }
           else code += current_callParams.get(i).getTempName();
       }
       current_callParams.clear();
       code += ")\n";
       emit(code);

       // if(m.getType().equals(INT_TYPE)){
       //     cur_func_return =  new VarInfo(INT_TYPE,FUNC_RET,null,callT,-1);
       // }
       // else if(m.getType().equals(BOOLEAN_TYPE)){
       //     cur_func_return = new VarInfo(BOOLEAN_TYPE,FUNC_RET,null,callT,-1);
       // }
       // else
       //     cur_func_return = new VarInfo(m.getType(),FUNC_RET,null,callT,-1);

       return callT;
    }



    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String extra) {
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       n.f2.accept(this, null);
       return null;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n, String extra) {
        this.isExprRetIntLiteral = true;
        cur_Expr_type = INT_TYPE;
       return n.f0.toString();
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String extra) {
        this.isExprRetBooleanLiteral = true;
        cur_Expr_type = BOOLEAN_TYPE;
       return "true";
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String extra) {
        this.isExprRetBooleanLiteral = true;
        cur_Expr_type = BOOLEAN_TYPE;
       return "false";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String extra) {
       String id = n.f0.toString();
       if(!current_class.equals("")){
           VarInfo v = getVarInfoFromIdentifier(id);
           if(v != null) cur_Expr_type = v.getType();
       }
       return id;
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String extra) {
        cur_Expr_type = current_class;
       return n.f0.toString();
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n, String extra){
        cur_Expr_type = INT_ARRAY_TYPE;
       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       n.f4.accept(this, null);
       return INT_ARRAY_TYPE;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n,  String extra) {
        emit("; AllocationExpression\n");
       String _ret=null;
       String code = "";
       n.f0.accept(this, null);
       String identifier = n.f1.accept(this, null);
       cur_Expr_type = identifier;
       int size = getClassSize(identifier);
       int method_count = symbol_table.get(identifier).getMethods().size();
       String objPtr = getNewTemp();
       code += objPtr+" = call i8* @calloc(i32 1, i32 "+(size+8)+")\n"; //+8 for vtable
       String castAddress = getNewTemp();
       code += castAddress+" = bitcast i8* "+objPtr+" to i8***\n";
       String getPtr = getNewTemp();
       code += getPtr+" = getelementptr ["+method_count+" x i8*], ["+method_count+" x i8*]* @."+identifier+"_vtable, i32 0, i32 0\n";
       code += "store i8** "+getPtr+", i8*** "+castAddress+"\n";
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       emit(code);
       return objPtr;
    }

    /**
     * f0 -> NotExpression()
     *       | PrimaryExpression()
     */
    public String visit(Clause n, String extra) {
       return n.f0.accept(this, null);
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, String extra){
        cur_Expr_type = BOOLEAN_TYPE;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       return BOOLEAN_TYPE;
    }

    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n, String extra) {
        String aa = n.f0.accept(this, null);
        return aa;
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(ArrayType n, String extra) {
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       n.f2.accept(this, null);
       return INT_ARRAY_TYPE;
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, String extra) {
        String _ret=n.f0.toString();
        return BOOLEAN_TYPE;
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, String extra) {
        String _ret=n.f0.toString();
        return INT_TYPE;
    }
}

