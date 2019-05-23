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

/**
 * Provides default methods which visit each node in the tree in depth-first
 * order.  Your visitors may extend this class.
 */
public class TypeCheckerVisitor extends GJNoArguDepthFirst<String> {
    HashMap<String, ClassData> symbol_table;  // class scoped symbol table:  <"class name", "class_data">

    String current_class;
    String current_class_inherit;
    ArrayList<VarData> current_vars;
    ArrayList<String> current_arg_types;
    boolean check_for_args;
    String current_method;
    String current_method_type;
    String current_var;
    String current_var_type;
    static final String NO_INHERIT = "no_inherit";
    static final String BOOLEAN_TYPE = "boolean";
    static final String INT_TYPE = "int";
    static final String INT_ARRAY_TYPE = "int[]";
    static final String INTEGER_LITERAL = "int_literal";
    static final String BOOLEAN_LITERAL = "bool_literal";
    LineNumberInfo last_line_info;


    public TypeCheckerVisitor(HashMap<String, ClassData> table){
        symbol_table = table; // get reference to symbol table
        current_vars = new ArrayList<VarData>();
        this.current_arg_types = new ArrayList<String>();
        this.current_class = null;
        this.current_class_inherit = null;
        this.current_method = null;
        this.current_method_type = null;
        this.current_var = null;
        this.current_var_type = null;
        this.last_line_info = null;
        this.check_for_args = false;
    }


    public TypeCheckerVisitor findClass(String name, String inherit){
        this.current_class = symbol_table.get(name).getName();
        if(inherit.equals(NO_INHERIT))
            this.current_class_inherit = NO_INHERIT;
        else
            this.current_class_inherit = symbol_table.get(inherit).getName();
        this.current_method = null;
        this.current_method_type = null;
        this.current_var = null;
        this.current_var_type = null;
        if(this.current_class == null)
            throw new IllegalStateException("[ERROR] Type "+name +" is not declared" + log_error_line());

        else if(this.current_class_inherit == null)
            throw new IllegalStateException("[ERROR] Type "+inherit +" which extends class " + name +" is not declared" + log_error_line());

        return this;
    }

    public TypeCheckerVisitor checkMethod(String name) throws IllegalStateException{
        MethodData m = symbol_table.get(this.current_class).getMethods().get(name);
        this.current_method = m.getName();
        if(this.current_method == null)
            throw new IllegalStateException("[Error] Method "+name +" is not declared" + log_error_line());

        if(symbol_table.get(symbol_table.get(this.current_class).getInheritance()) != null){
            MethodData overriddenMethod = symbol_table.get(symbol_table.get(this.current_class).getInheritance()).getMethods().get(name);
            if(overriddenMethod != null){
                if(!overriddenMethod.getType().equals(m.getType()))
                    throw new IllegalStateException("[ERROR] Overriden Method return type mismatch" +log_error_line());
                if(overriddenMethod.getArgs().size() != m.getArgs().size())
                    throw new IllegalStateException("[ERROR] Overriden Method Arguments number mismatch" + log_error_line());
                int i = 0;
                for (VarData arg : overriddenMethod.getArgs()) {
                    if(!arg.getType().equals(m.getArgs().get(i).getType()))
                        throw new IllegalStateException("[ERROR] Overriden Method Argument "+(i+1)+" type mismatch" + log_error_line());
                    i += 1;
                }
            }
        }
        return this;
    }
    // returns method type if found else error
    public String checkMethodCall(String obj_name, String fn_name) throws IllegalStateException{
        String obj_type;
        if(obj_name.equals("this"))
            obj_type = this.current_class;
        else
            obj_type = findVariable(obj_name,false);

        if(symbol_table.get(obj_type).getMethods().get(fn_name) == null)
            throw new IllegalStateException("[Error] Object of type "+ obj_type+" has no method "+fn_name + log_error_line());

        int i = 0;
        if(symbol_table.get(obj_type).getMethods().get(fn_name).getArgs().size() != current_arg_types.size())
            throw new IllegalStateException("[Error] Expecting more arguments in method call" + log_error_line());

        for (VarData v : symbol_table.get(obj_type).getMethods().get(fn_name).getArgs()) {
            if(!v.getType().equals(current_arg_types.get(i)))
                throw new IllegalStateException("[Error] Argument "+ (i+1) +" of method type mismatch" + log_error_line());

            i += 1;
        }
        return symbol_table.get(obj_type).getMethods().get(fn_name).getType();
    }

    public void checkType(String type) throws IllegalStateException{
        if(!type.equals(INT_TYPE) && !type.equals(BOOLEAN_TYPE) && !type.equals(INT_ARRAY_TYPE)){
            if(symbol_table.get(type) == null)
                throw new IllegalStateException("[ERROR] Type: "+type+" is not declared"  + log_error_line());

        }
    }

    public void initializeVar(String name){

        for (VarData v : symbol_table.get(this.current_class).getMethods().get(this.current_method).getVars()) { //search as method var
            if(v.getName().equals(name)){
                v.setInitialized();
                return;
            }
        }

        for (VarData v : symbol_table.get(this.current_class).getVars()) {
            if(v.getName().equals(name)){
                v.setInitialized();
            }
        }
    }
    // returns variable type if found else error
    public String findVariable(String name, Boolean requireInitialized) throws IllegalStateException{
        this.current_var = null;
        this.current_var_type = null;

        //search as argument
        if(symbol_table.get(this.current_class).getMethods().get(this.current_method) != null){
            for (VarData v : symbol_table.get(this.current_class).getMethods().get(this.current_method).getArgs()) {
                if(v.getName().equals(name)){
                    checkType(v.getType());
                    this.current_var = v.getName();
                    this.current_var_type = v.getType();
                    return this.current_var_type;
                }
            }
        }

        if(symbol_table.get(this.current_class).getMethods().get(this.current_method) != null){
            for (VarData v : symbol_table.get(this.current_class).getMethods().get(this.current_method).getVars()) { //search as method var
                if(v.getName().equals(name)){
                    checkType(v.getType());
                    this.current_var = v.getName();
                    this.current_var_type = v.getType();
                    if(requireInitialized == true && v.isInitialized() == false)
                        throw new IllegalStateException("[ERROR] Variable "+name +" may not be initialized" + log_error_line());
                    return this.current_var_type;
                }
            }
        }

        // search in class scope
        for (VarData v : symbol_table.get(this.current_class).getVars()) {
            if(v.getName().equals(name)){
                checkType(v.getType());
                this.current_var = v.getName();
                this.current_var_type = v.getType();
                if(requireInitialized == true && v.isInitialized() == false)
                    throw new IllegalStateException("[ERROR] Variable "+name +" may not be initialized" + log_error_line());
                return this.current_var_type;
            }
        }

        // search in superlass scope
        if(symbol_table.get(symbol_table.get(this.current_class).getInheritance()) != null){
            for (VarData v : symbol_table.get(symbol_table.get(this.current_class).getInheritance()).getVars()) {
                if(v.getName().equals(name)){
                    checkType(v.getType());
                    this.current_var = v.getName();
                    this.current_var_type = v.getType();
                    if(requireInitialized == true && v.isInitialized() == false)
                        throw new IllegalStateException("[ERROR] Variable "+name +" may not be initialized" + log_error_line());
                    return this.current_var_type;
                }
            }
        }

        if(this.current_var == null)
            throw new IllegalStateException("[ERROR] Variable "+name +" is not declared" + log_error_line());

        return null;
    }

    public String log_error_line(){
        return " at line "+ last_line_info.toString();
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
       String class_name = n.f1.accept(this);
       findClass(class_name, NO_INHERIT);
       n.f2.accept(this);
       n.f3.accept(this);
       n.f4.accept(this);
       n.f5.accept(this);
       n.f6.accept(this);
       checkMethod("main");
       n.f7.accept(this);
       n.f8.accept(this);
       n.f9.accept(this);
       n.f10.accept(this);
       n.f12.accept(this);
       n.f13.accept(this);
       n.f14.accept(this);
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
       findClass(cname, NO_INHERIT);
       n.f2.accept(this);
       n.f3.accept(this);
       n.f4.accept(this);
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
       n.f2.accept(this);
       String inherit = n.f3.accept(this);
       findClass(cname, inherit);
       n.f4.accept(this);
       n.f5.accept(this);
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
    public String visit(MethodDeclaration n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String type = n.f1.accept(this);
       checkType(type); // check return type
       String name = n.f2.accept(this);
       this.current_method = name;
       n.f3.accept(this);
       n.f4.accept(this);

       checkMethod(name);
       n.f5.accept(this);
       n.f6.accept(this);
       n.f7.accept(this);

       n.f8.accept(this);
       n.f9.accept(this);
       String return_type = n.f10.accept(this);
       if(return_type.equals(BOOLEAN_LITERAL)) return_type = BOOLEAN_TYPE;
       if(return_type.equals(INTEGER_LITERAL)) return_type = INT_TYPE;
       if(!return_type.equals(INT_TYPE) && !return_type.equals(BOOLEAN_TYPE) && !return_type.equals(INT_ARRAY_TYPE)){
           String type_from_identifier = findVariable(return_type,false);
           if(!type.equals(type_from_identifier))
                throw new IllegalStateException("[ERROR] Method return type does not match actual return type" +log_error_line());
       }
       else{
           if(!type.equals(return_type))
                throw new IllegalStateException("[ERROR] Method return type does not match actual return type" +log_error_line());
       }
       n.f11.accept(this);
       n.f12.accept(this);
       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String type = n.f0.accept(this);
       String name = n.f1.accept(this);
       String type_found = findVariable(name,false); //for class objects
       if(!type_found.equals(type))
           throw new IllegalStateException("[ERROR] Argument "+ name + "has type "+ type  + log_error_line());

       return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String type = n.f0.accept(this);
       String name = n.f1.accept(this);
       n.f2.accept(this);
       String type_found = findVariable(name,false); //fo`r class objects
       if(!type_found.equals(type))
           throw new IllegalStateException("[ERROR] Variable "+ name + "has type "+ type + log_error_line());

       return null;

    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String id1 = n.f0.accept(this);
       String id1_type = findVariable(id1,false);
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(BOOLEAN_LITERAL)) expr_type = BOOLEAN_TYPE;
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INT_TYPE;
       n.f3.accept(this);
       if(!expr_type.equals(id1_type))
           throw new IllegalStateException("[ERROR] Assignment on different types" + log_error_line());

       initializeVar(id1);
       return id1_type;
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
    public String visit(ArrayAssignmentStatement n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String name = n.f0.accept(this);
       String type = findVariable(name,false);
       if(!type.equals(INT_ARRAY_TYPE))
        throw  new IllegalStateException("[ERROR] Trying to assign a non array" + log_error_line());
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INT_TYPE;
       if(!expr_type.equals(INT_TYPE)){
           expr_type = findVariable(expr_type,false);
           if(!expr_type.equals(INT_TYPE))
            throw new IllegalStateException("[ERROR] Trying to assign an invalid member of array" + log_error_line());
       }
       n.f3.accept(this);
       n.f4.accept(this);
       String expr_type2 = n.f5.accept(this);
       if(expr_type2.equals(INTEGER_LITERAL)) expr_type2 = INT_TYPE;
       if(!expr_type2.equals(INT_TYPE)){
           expr_type2 = findVariable(expr_type2,false);
           if(!expr_type2.equals(INT_TYPE))
            throw new IllegalStateException("[ERROR] Trying to assign a non int value in array member" + log_error_line());
       }
       n.f6.accept(this);
       initializeVar(name);
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
    public String visit(IfStatement n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(BOOLEAN_LITERAL)) expr_type = BOOLEAN_TYPE;
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INTEGER_LITERAL;
       if(expr_type.equals(INT_ARRAY_TYPE) || expr_type.equals(INT_TYPE))
           throw new IllegalStateException("[ERROR] Expression type in if statement is not boolean"+log_error_line());

       if(!expr_type.equals(BOOLEAN_TYPE)){
            expr_type = findVariable(expr_type,false);
            if(!expr_type.equals(BOOLEAN_TYPE))
                throw new IllegalStateException("[ERROR] Expression type in if statement is not boolean"+log_error_line());
        }
       n.f3.accept(this);
       n.f4.accept(this);
       n.f5.accept(this);
       n.f6.accept(this);
       return _ret;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(BOOLEAN_LITERAL)) expr_type = BOOLEAN_TYPE;
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INTEGER_LITERAL;
       if(expr_type.equals(INT_ARRAY_TYPE) || expr_type.equals(INT_TYPE))
           throw new IllegalStateException("[ERROR] Expression type in if statement is not boolean"+log_error_line());

       if(!expr_type.equals(BOOLEAN_TYPE)){
            expr_type = findVariable(expr_type,false);
            if(!expr_type.equals(BOOLEAN_TYPE))
                throw new IllegalStateException("[ERROR] Expression type in if statement is not boolean"+log_error_line());
        }
       n.f3.accept(this);
       n.f4.accept(this);
       return _ret;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(BOOLEAN_LITERAL)) expr_type = BOOLEAN_TYPE;
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INTEGER_LITERAL;
       if(expr_type.equals(INT_ARRAY_TYPE))
           throw new IllegalStateException("[ERROR] Expression type in print statement is not int or boolean"+log_error_line());

       if(!expr_type.equals(INT_TYPE) && !expr_type.equals(BOOLEAN_TYPE)){
            expr_type = findVariable(expr_type,false);
            if(!expr_type.equals(INT_TYPE) && !expr_type.equals(BOOLEAN_TYPE))
                throw new IllegalStateException("[ERROR] Expression type in print statement is not int or boolean"+log_error_line());
        }
       n.f3.accept(this);
       n.f4.accept(this);
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
    public String visit(Expression n) {
        last_line_info = LineNumberInfo.get(n);
        if(this.check_for_args == true){
            String return_type = n.f0.accept(this);
            if(return_type.equals(BOOLEAN_LITERAL)) return_type = BOOLEAN_TYPE;
            if(return_type.equals(INTEGER_LITERAL)) return_type = INTEGER_LITERAL;
            if(!return_type.equals(INT_TYPE) && !return_type.equals(BOOLEAN_TYPE) && !return_type.equals(INT_ARRAY_TYPE))
                return_type = findVariable(return_type,false);

            this.current_arg_types.add(return_type);
            return null;
        }
       return n.f0.accept(this);
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
        String _ret=null;
        String op1 = n.f0.accept(this);
        n.f1.accept(this);
        String op2 = n.f2.accept(this);
        String type1;
        String type2;

        if(op1.equals(INTEGER_LITERAL))
            type1 = INT_TYPE;
        else if(op1.equals(BOOLEAN_LITERAL))
            type1 = BOOLEAN_TYPE;
        else
            type1 = findVariable(op1,true);

        if(op2.equals(INTEGER_LITERAL))
            type2 = INT_TYPE;
        else if(op2.equals(BOOLEAN_LITERAL))
            type2 = BOOLEAN_TYPE;
        else
            type2 = findVariable(op2,true);

        if(!type1.equals(type2))
            throw new IllegalStateException("[ERROR] and expression with operand type mismatch" + log_error_line());


        _ret = type1; // return expression type
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
        String _ret=null;
        String op1 = n.f0.accept(this);
        n.f1.accept(this);
        String op2 = n.f2.accept(this);
        if(op1.equals(INTEGER_LITERAL) && !op2.equals(INTEGER_LITERAL)){
            String type2 = findVariable(op2,true);
            if(!type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] compare expression with no integer operands" + log_error_line());

        }
        else if(op2.equals(INTEGER_LITERAL) && !op1.equals(INTEGER_LITERAL)){
            String type1 = findVariable(op1,true);
            if(!type1.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] compare expression with no integer operands" + log_error_line());

        }
        else if(op2.equals(INTEGER_LITERAL) && op1.equals(INTEGER_LITERAL)){

        }
        else{
            String type1 = findVariable(op1,true);
            String type2 = findVariable(op2,true);
            if(!type1.equals(INT_TYPE) || !type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] compare expression with no integer operands" + log_error_line());

        }

        _ret = INT_TYPE; // return expression type
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String op1 = n.f0.accept(this);
       n.f1.accept(this);
       String op2 = n.f2.accept(this);
       if(op1.equals(INTEGER_LITERAL) && !op2.equals(INTEGER_LITERAL)){
           String type2 = findVariable(op2,true);
           if(!type2.equals(INT_TYPE))
               throw new IllegalStateException("[ERROR] plus expression with no integer operands" + log_error_line());

       }
       else if(op2.equals(INTEGER_LITERAL) && !op1.equals(INTEGER_LITERAL)){
           String type1 = findVariable(op1,true);
           if(!type1.equals(INT_TYPE))
               throw new IllegalStateException("[ERROR] plus expression with no integer operands" + log_error_line());

       }
       else if(op2.equals(INTEGER_LITERAL) && op1.equals(INTEGER_LITERAL)){

       }
       else{
           String type1 = findVariable(op1,true);
           String type2 = findVariable(op2,true);
           if(!type1.equals(INT_TYPE) || !type2.equals(INT_TYPE))
               throw new IllegalStateException("[ERROR] plus expression with no integer operands" + log_error_line());

       }

       _ret = INT_TYPE; // return expression type
       return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
        String _ret=null;
        String op1 = n.f0.accept(this);
        n.f1.accept(this);
        String op2 = n.f2.accept(this);
        if(op1.equals(INTEGER_LITERAL) && !op2.equals(INTEGER_LITERAL)){
            String type2 = findVariable(op2,true);
            if(!type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] minus expression with no integer operands" + log_error_line());
        }
        else if(op2.equals(INTEGER_LITERAL) && !op1.equals(INTEGER_LITERAL)){
            String type1 = findVariable(op1,true);
            if(!type1.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] minus expression with no integer operands" + log_error_line());

        }
        else if(op2.equals(INTEGER_LITERAL) && op1.equals(INTEGER_LITERAL)){

        }
        else{
            String type1 = findVariable(op1,true);
            String type2 = findVariable(op2,true);
            if(!type1.equals(INT_TYPE) || !type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] minus expression with no integer operands" + log_error_line());

        }

        _ret = INT_TYPE; // return expression type
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
        String _ret=null;
        String op1 = n.f0.accept(this);
        n.f1.accept(this);
        String op2 = n.f2.accept(this);
        if(op1.equals(INTEGER_LITERAL) && !op2.equals(INTEGER_LITERAL)){
            String type2 = findVariable(op2,true);
            if(!type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] times expression with no integer operands" + log_error_line());

        }
        else if(op2.equals(INTEGER_LITERAL) && !op1.equals(INTEGER_LITERAL)){
            String type1 = findVariable(op1,true);
            if(!type1.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] times expression with no integer operands" + log_error_line());
        }
        else if(op2.equals(INTEGER_LITERAL) && op1.equals(INTEGER_LITERAL)){

        }
        else{
            String type1 = findVariable(op1,true);
            String type2 = findVariable(op2,true);
            if(!type1.equals(INT_TYPE) || !type2.equals(INT_TYPE))
                throw new IllegalStateException("[ERROR] times expression with no integer operands" + log_error_line());
        }

        _ret = INT_TYPE; // return expression type
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String identifier = n.f0.accept(this);
       String identifier_type = findVariable(identifier,false);
       if(!identifier_type.equals(INT_ARRAY_TYPE))
            throw new IllegalStateException("[ERROR] Trying to lookup a non array type" + log_error_line());
       n.f1.accept(this);
       String expr_type = n.f2.accept(this);
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INT_TYPE;
       if(!expr_type.equals(INT_TYPE))
            throw new IllegalStateException("[ERROR] Trying to lookup an invalid array index" + log_error_line());
       n.f3.accept(this);
       return INT_TYPE;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String identifier = n.f0.accept(this);
       String id_type = findVariable(identifier,false);
       if(!id_type.equals(INT_ARRAY_TYPE))
            throw new IllegalStateException("[ERROR] Trying to access length property of non array" + log_error_line());
       n.f1.accept(this);
       n.f2.accept(this);
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
    public String visit(MessageSend n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       String obj = n.f0.accept(this);
       n.f1.accept(this);
       String fn_name = n.f2.accept(this);
       n.f3.accept(this);
       this.check_for_args = true;
       n.f4.accept(this);
       n.f5.accept(this);
       String method_type = checkMethodCall(obj,fn_name); //will evaluate args from current_arg_types arraylist
       this.check_for_args = false;
       current_arg_types.clear();
       return method_type;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n) {
       n.f0.accept(this);
       String expr_type = n.f1.accept(this);
       n.f2.accept(this);
       return expr_type;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public String visit(IntegerLiteral n) {
       return INTEGER_LITERAL;
    }

    /**
     * f0 -> "true"
     */
    public String visit(TrueLiteral n) {
       return BOOLEAN_LITERAL;
    }

    /**
     * f0 -> "false"
     */
    public String visit(FalseLiteral n) {
       return BOOLEAN_LITERAL;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public String visit(Identifier n) {
       String id = n.f0.toString();
       return id;
    }

    /**
     * f0 -> "this"
     */
    public String visit(ThisExpression n) {
       return n.f0.toString();
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(ArrayAllocationExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       n.f1.accept(this);
       n.f2.accept(this);
       String expr_type = n.f3.accept(this);
       if(expr_type.equals(INTEGER_LITERAL)) expr_type = INT_TYPE;
       if(!expr_type.equals(INT_TYPE)){
           expr_type = findVariable(expr_type,true);
           if(!expr_type.equals(INT_TYPE))
            throw new IllegalStateException("[ERROR] Cannot allocate a non int value" + log_error_line());
       }
       n.f4.accept(this);

       return INT_ARRAY_TYPE;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n) {
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String identifier = n.f1.accept(this);
       checkType(identifier);
       n.f2.accept(this);
       n.f3.accept(this);
       return identifier;
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n) throws IllegalStateException{
        last_line_info = LineNumberInfo.get(n);
       String _ret=null;
       n.f0.accept(this);
       String clause_type = n.f1.accept(this);
       if(clause_type.equals(BOOLEAN_LITERAL)) clause_type = BOOLEAN_TYPE;
       if(!clause_type.equals(BOOLEAN_TYPE))
        throw new IllegalStateException("[ERROR] Not a boolean expression" + log_error_line());
       return BOOLEAN_TYPE;
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
       return INT_ARRAY_TYPE;
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
}
