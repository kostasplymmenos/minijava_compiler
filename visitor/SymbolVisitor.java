package visitor;
import helpers.VarData;
import helpers.MethodData;
import helpers.ClassData;
import syntaxtree.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import helpers.LineNumberInfo;


/**
 * Provides default methods which visit each node in the tree in depth-first
 * order.  Your visitors may extend this class.
 */
public class SymbolVisitor extends GJNoArguDepthFirst<String>{
    HashMap<String, ClassData> symbol_table;  // class scoped symbol table:  <"class name", "class_data">

    String current_class;
    ArrayList<VarData> current_vars;
    ArrayList<VarData> current_args;
    String current_method;
    int varOffset;
    int fnOffset;
    LineNumberInfo last_line_info;
    static final int BOOLEAN_SZ = 1;
    static final int INT_SZ = 4;
    static final int PTR_SZ = 8;


    public SymbolVisitor(){
        symbol_table = new HashMap<String, ClassData >(); //intialize hashmap
        current_vars = new ArrayList<VarData>();
        current_args = new ArrayList<VarData>();
        varOffset = 0;
        fnOffset = 0;
    }

    public void incVarOffset(String type, int n){
        if(type == "int")
            varOffset += INT_SZ;
        else if (type == "boolean")
            varOffset += BOOLEAN_SZ;
        else if (type == "int[]")
            varOffset += PTR_SZ;
        else
            varOffset += PTR_SZ;
    }

    public void incFnOffset(){
        fnOffset += PTR_SZ;
    }

    public String log_error_line(){
        return " at line "+ last_line_info.toString();
    }

    public HashMap<String, ClassData> getSymbolTable(){
        return symbol_table;
    }

    public void printData(){
        for (String name : symbol_table.keySet()) {
            System.out.println("\nClass: "+ name);
            System.out.println("\ninherits from class: " + symbol_table.get(name).getInheritance());
            System.out.println("\n\tClass Vars: ");
            for (VarData v : symbol_table.get(name).getVars()) {
                System.out.println("\t\t"+ v.getType() + " " + v.getName()+ " Mem Offset: "+v.getOffset());
            }
            System.out.println("\n\tClass Methods: ");
            for (String mName : symbol_table.get(name).getMethods().keySet()) {
                System.out.println("\n\t - "+ mName + " Mem Offset: "+symbol_table.get(name).getMethods().get(mName).getOffset());
                System.out.println("\n\t\tMethod Args: ");
                for (VarData arg : symbol_table.get(name).getMethods().get(mName).getArgs()){
                    System.out.println("\t\t\t"+arg.getType()+" "+arg.getName());
                }
                System.out.println("\n\t\tMethod Vars: ");
                for (VarData var : symbol_table.get(name).getMethods().get(mName).getVars()){
                    System.out.println("\t\t\t"+var.getType()+" "+var.getName() + " Mem Offset: "+var.getOffset());
                }
            }
        }
    }

    public void printOffsets(){
        for (String name : symbol_table.keySet()) {
            for (VarData v : symbol_table.get(name).getVars()) {
                System.out.println(name+"."+v.getName()+": "+v.getOffset());
            }
            for (MethodData m : symbol_table.get(name).getMethods().values()) {
                if(!m.getName().equals("main"))
                    System.out.println(name+"."+m.getName()+": "+m.getOffset());

            }
        }
    }

    public void addNewClass(String name,String inherit) throws IllegalStateException{
        if(this.symbol_table.get(name) != null)
            throw new IllegalStateException("[ERROR] Redeclaration of class "+ name + log_error_line());

        if(inherit != null){
            if(this.symbol_table.get(inherit) == null)
                throw new IllegalStateException("[ERROR] Superclass is not declared"+ log_error_line());
            this.symbol_table.put(name, new ClassData(name,inherit));
        }
        else
            this.symbol_table.put(name, new ClassData(name));
    }

    public void addVarToMethod(VarData v) throws IllegalStateException{ //adds vardata to current class current method
        for (VarData var : this.symbol_table.get(current_class).getMethods().get(current_method).getVars()) {
            if(var.getName() == v.getName()){
                throw new IllegalStateException("[ERROR] Redeclaration of variable "+ v.getName() + " in class: " + current_class + " in method: "+ current_method);
            }
        }
        this.symbol_table.get(current_class).addVarDataToMethod(current_method,v);
    }
    public void addVarToClass(VarData v) throws IllegalStateException{ //adds vardata to current class
        for (VarData var : this.symbol_table.get(current_class).getVars()) {
            if(var.getName() == v.getName())
                throw new IllegalStateException("[ERROR] Redeclaration of variable "+ v.getName() + " in class: " + current_class);
        }
        this.symbol_table.get(current_class).getVars().add(v);
    }
    public void addMethodToClass(MethodData m) throws IllegalStateException{ //adds MethodData to current class
        if(this.symbol_table.get(current_class).getMethods().get(m.getName()) != null)
            throw new IllegalStateException("[ERROR] Redeclaration of function "+ m.getName() + " in class: " + current_class);

        this.symbol_table.get(current_class).addMethodData(m);
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
    public String visit(MainClass n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String mainClassName = n.f1.accept(this);
       this.current_class = mainClassName;
       addNewClass(mainClassName,null);
       n.f2.accept(this);
       n.f3.accept(this);
       n.f4.accept(this);
       n.f5.accept(this);
       n.f6.accept(this);
       // create new method main
       MethodData mainMethod = new MethodData("void","main",-1);
       this.current_method = "main";
       n.f7.accept(this);
       n.f8.accept(this);
       n.f9.accept(this);
       n.f10.accept(this);

       // add main method's arg
       String argId = n.f11.accept(this);
       mainMethod.addArg(new VarData("String[]",argId, varOffset));

       n.f12.accept(this);
       n.f13.accept(this);

       // handle main method's vars
       n.f14.accept(this);

       for (VarData v : this.current_vars) {
           mainMethod.addVar(v);
       }
       this.current_vars.clear();
       //finish and add
       addMethodToClass(mainMethod);

       n.f15.accept(this);
       n.f16.accept(this);
       n.f17.accept(this);


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
    public String visit(ClassDeclaration n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String cname = n.f1.accept(this);
       this.current_class = cname;
       addNewClass(cname,null);
       n.f2.accept(this);
       n.f3.accept(this);
       for (VarData v : this.current_vars) {
           v.setOffset(this.varOffset);
           incVarOffset(v.getType(),1);
           addVarToClass(v);
       }
       current_vars.clear();
       n.f4.accept(this);
       //methods

       n.f5.accept(this);
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
    public String visit(ClassExtendsDeclaration n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String cname = n.f1.accept(this);
       this.current_class = cname;
       n.f2.accept(this);
       String inherit = n.f3.accept(this);
       addNewClass(cname,inherit);
       n.f4.accept(this);
       n.f5.accept(this);
       for (VarData v : this.current_vars) {
           v.setOffset(this.varOffset);
           incVarOffset(v.getType(),1);
           addVarToClass(v);
       }
       current_vars.clear();
       n.f6.accept(this);
       n.f7.accept(this);
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
    public String visit(MethodDeclaration n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String type = n.f1.accept(this);
       String name = n.f2.accept(this);
       this.current_method = name;
       MethodData mdata = new MethodData(type,name, fnOffset);
       incFnOffset();
       n.f3.accept(this);
       n.f4.accept(this);
       // add args to method data
       for (VarData arg : this.current_args) {
           mdata.addArg(arg);
       }
       this.current_args.clear();
       addMethodToClass(mdata);
       n.f5.accept(this);
       n.f6.accept(this);
       n.f7.accept(this);
       for (VarData v : this.current_vars)
           addVarToMethod(v);
       this.current_vars.clear();

       n.f8.accept(this);
       n.f9.accept(this);
       n.f10.accept(this);
       n.f11.accept(this);
       n.f12.accept(this);
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String type = n.f0.accept(this);
       String name = n.f1.accept(this);
       this.current_args.add(new VarData(type,name));
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n) {
        last_line_info = LineNumberInfo.get(n);
       String type = n.f0.accept(this);
       String name = n.f1.accept(this);
       n.f2.accept(this);
       current_vars.add(new VarData(type, name, varOffset));
       return null;

    }

    /**
     * f0 -> ArrayType()
     *       | BooleanType()
     *       | IntegerType()
     *       | Identifier()
     */
    public String visit(Type n) {
        return n.f0.accept(this);
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public String visit(ArrayType n) {
       String _ret=null;
       n.f0.accept(this);
       n.f1.accept(this);
       n.f2.accept(this);
       return "int[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n) {
        String _ret=n.f0.toString();
        return _ret;
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n) {
        String _ret=n.f0.toString();
        return _ret;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n) {
        String _ret=n.f0.toString();
        return _ret;
    }
}
