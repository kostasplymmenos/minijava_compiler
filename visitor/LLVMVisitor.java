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
    String id;
    String tempName;

    public VarInfo(String t,String id,String temp){
        this.type = t;
        this.id = id;
        this.tempName = temp;
    }
    public String getTempName(){ return this.tempName; }
    public String getId(){ return this.id; }
    public String getType(){ return this.type; }
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
    static final String BOOLEAN_TYPE = "boolean";
    static final String INT_TYPE = "int";
    static final String INT_ARRAY_TYPE = "int[]";
    static final String INTEGER_LITERAL = "int_literal";
    static final String BOOLEAN_LITERAL = "bool_literal";

    public LLVMVisitor(HashMap<String, ClassData> table){
        symbol_table = table; // get reference to symbol table
        code_buffer = new StringBuffer();
        current_vars = new ArrayList<VarInfo>();
        current_args = new ArrayList<VarInfo>();
        temp_count = 0;
        label_count = 0;
        isExprRetBooleanLiteral = false;
        isExprRetIntLiteral = false;

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
       vtableCode = "@."+className+"_vtable = global["+class_method_count+" x i8*] ";
       for (MethodData m : symbol_table.get(className).getMethods().values()) {
           vtableCode += "[i8* bitcast (";
           vtableCode += m.getType().equals(INT_TYPE) ? " i32 (i8*" : " i1 (i8*";
           for (VarData arg : m.getArgs()) {
               vtableCode += arg.getType().equals(INT_TYPE) ? ", i32" : ", i1";
           }
           vtableCode += ")* @"+className+"."+m.getName()+" to i8*)] ";
       }
       n.f2.accept(this, null);
       n.f3.accept(this, "heap");
       n.f4.accept(this, null);
       n.f5.accept(this, null);
       emitOnTop(vtableCode+"\n");
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
       code += "@"+current_class+"."+methodName+"(i8* this ";
       for (VarData arg : m.getArgs()) {
           code += ", ";
           code += arg.getType().equals(INT_TYPE) ? "i32 %" : "i1 %";
           code += arg.getName();
       }
       code += ") {\n";
       for (VarData arg : m.getArgs()) {
           String argTemp = getNewTemp();
           current_args.add(new VarInfo(arg.getType(),arg.getName(),argTemp));
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
       n.f10.accept(this, null);
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
            // else if(type.equals(INT_ARRAY_TYPE))
            //     emit("%"+temp+" = alloca i32");
            String id = n.f1.accept(this, null);
            n.f2.accept(this, null);
            current_vars.add(new VarInfo(type, id, temp));
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
       for (VarInfo v : current_vars) { // if expression is  a literal
           if(v.getId().equals(id)){
               if(this.isExprRetBooleanLiteral && expr_ret.equals("false")){
                   emit("store i1 0, i1* "+v.getTempName()+"\n");
                   return null;
               }
               if(this.isExprRetBooleanLiteral && expr_ret.equals("true")){
                   emit("store i1 1, i1* "+v.getTempName()+"\n");
                   return null;
               }
               if(this.isExprRetIntLiteral){
                   emit("store i32 "+ expr_ret +", i32* "+v.getTempName()+"\n");
                   return null;
               }

               for (VarInfo v2 : current_vars) { //if expression() is identifier
                   if(v2.getId().equals(expr_ret) && v2.getType().equals(INT_TYPE)){
                        emit("store i32 "+v2.getTempName()+", i32* "+v.getTempName()+"\n");
                        return null;
                   }
                   if(v2.getId().equals(expr_ret) && v2.getType().equals(BOOLEAN_TYPE)){
                        emit("store i1 "+v2.getTempName()+", i1* "+v.getTempName()+"\n");
                        return null;
                   }
               }
               // if expression is a temp var
               if(v.getType().equals(INT_TYPE))
                     emit("store i32 "+expr_ret+", i32* "+v.getTempName()+"\n");
               else if(v.getType().equals(BOOLEAN_TYPE))
                     emit("store i1 "+expr_ret+", i1* "+v.getTempName()+"\n");
               return null;
           }
       }
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

       String _ret=null;
       n.f0.accept(this, null);
       String if_label = getNewLabel();
       String else_label = getNewLabel();
       String out_label = getNewLabel();
       n.f1.accept(this, null);
       String temp = n.f2.accept(this, null);
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
       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       String temp = n.f2.accept(this, null);
       for (VarInfo v : current_vars) { //for identifiers
           if(v.getId().equals(temp) && v.getType().equals(INT_TYPE)){
               String loadT = getNewTemp();
               emit(loadT + " = load i32, i32* "+v.getTempName()+"\n");
               emit("call void (i32) @print_int(i32 "+ loadT +")\n");
               return null;
           }
           else if(v.getId().equals(temp) && v.getType().equals(BOOLEAN_TYPE)){
               String loadT = getNewTemp();
               emit(loadT + " = load i1, i1* "+v.getTempName()+"\n");
               emit("call void (i32) @print_boolean(i1 "+ loadT +")\n");
               return null;
           }
       }
       n.f3.accept(this, null);
       n.f4.accept(this, null);
       emit("call void (i32) @print_int(i32 %"+ temp +")\n");
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

        String _ret=null;
        String newTemp = getNewTemp();
        String toEmit = newTemp + " = icmp slt i32 ";

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
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String extra){

       String _ret=null;
       String newTemp = getNewTemp();
       String toEmit = newTemp + " = add i32 ";

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
           for (VarInfo v : current_args) { // if primary expr is local var
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
           for (VarInfo v : current_args) { // if primary expr is local var
               if(v.getId().equals(id2)){
                   String loadT = getNewTemp();
                   emit(loadT+" = load i32, i32* "+v.getTempName() +"\n");
                   toEmit += loadT + "\n"; //TODO change for bracket expr
               }
           }
       }
       emit(toEmit);
       return newTemp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String extra){

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
    public String visit(MessageSend n, String extra) {

       String _ret=null;
       n.f0.accept(this, null);
       n.f1.accept(this, null);
       n.f2.accept(this, null);
       n.f3.accept(this, null);

       n.f4.accept(this, null);
       n.f5.accept(this, null);

       return _ret;
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
       return n.f0.toString();
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n, String extra) {
        this.isExprRetBooleanLiteral = true;
       return "true";
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n, String extra) {
        this.isExprRetBooleanLiteral = true;
       return "false";
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n, String extra) {
       String id = n.f0.toString();
       return id;
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n, String extra) {
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
       String _ret=null;
       String code = "";
       n.f0.accept(this, null);
       String identifier = n.f1.accept(this, null);
       int size = getClassSize(identifier);
       int method_count = symbol_table.get(identifier).getMethods().size();
       String objPtr = getNewTemp();
       code += objPtr+" = call i8* @calloc(i32 1, i32 "+(size+8)+")\n"; //+8 for vtable
       String castAddress = getNewTemp();
       code += castAddress+" bitcast i8* "+objPtr+" to i8***\n";
       String getPtr = getNewTemp();
       code += getPtr+" getelementptr ["+method_count+" x i8*], ["+method_count+" x i8*]* @."+identifier+"_vtable, i32 0, i32 0\n";
       code += "store i8** "+getPtr+", i8*** "+castAddress+"\n";
       n.f2.accept(this, null);
       n.f3.accept(this, null);
       emit(code);
       return _ret;
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
        System.out.println(aa);
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
