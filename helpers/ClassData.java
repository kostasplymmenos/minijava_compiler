package helpers;
import syntaxtree.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class ClassData{
    ArrayList<VarData> vars;
    HashMap<String, MethodData > methods;
    String inherit;
    String name;
    int lineIndex;

    public ClassData(String name){
        this.vars = new ArrayList<VarData>();
        this.methods = new HashMap<String, MethodData>();
        this.inherit = null;
        this.name = name;
    }

    public ClassData(String name, String inherit){
        this.vars = new ArrayList<VarData>();
        this.methods = new HashMap<String, MethodData>();
        this.inherit = inherit;
        this.name = name;
    }

    public ClassData(String name, String inherit, int l){
        this.vars = new ArrayList<VarData>();
        this.methods = new HashMap<String, MethodData>();
        this.inherit = inherit;
        this.name = name;
        this.lineIndex = l;
    }

    public ArrayList<VarData> getVars(){
        return this.vars;
    }

    public String getName(){
        return this.name;
    }

    public String getInheritance(){
        return this.inherit;
    }

    public HashMap<String, MethodData > getMethods(){
        return this.methods;
    }

    public void addMethodData(MethodData mdata){
        String mName = mdata.getName();
        methods.put(mName, mdata);

    }

    public void addVarDataToMethod(String mName, VarData v){
        for (String name : methods.keySet()) {
            if(mName.equals(name)){
                methods.get(name).addVar(v);
                return;
            }
        }
        System.out.println("[ERROR] Cannot find method to add var");
    }

    public void addVarData(VarData vdata){
        this.vars.add(vdata);
    }

}
