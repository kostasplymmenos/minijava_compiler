package helpers;
import syntaxtree.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;


public class VarData {
    String type;
    String name;
    int offset;
    boolean initialized;

    public VarData(String t, String n){
        this.type = t;
        this.name = n;
        this.initialized = false;
    }

    public VarData(String t, String n, int offset){
        this.type = t;
        this.name = n;
        this.offset = offset;
        this.initialized = false;
    }

    public void setInitialized(){
        this.initialized = true;
    }

    public void setOffset(int o){
        this.offset = o;
    }

    public String getType(){
        return this.type;
    }

    public String getName(){
        return this.name;
    }

    public boolean isInitialized(){
        return this.initialized;
    }
    public int getOffset(){
        return this.offset;
    }
}
