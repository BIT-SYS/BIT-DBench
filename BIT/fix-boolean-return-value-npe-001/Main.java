package com.github.hcsp.datatype;

public class Main {
    // 修复com/github/hcsp/datatype/Light.java，使得运行这个方法不会抛出空指针异常
    public static void main(String[] args) {
        Light light1 = new Light(null);
        boolean on1 = light1.isOn();
        System.out.println("状态未知的灯亮着吗：" + on1);

        Light light2 = new Light(true);
        boolean on2 = light2.isOn();
        System.out.println("亮着的灯亮着吗：" + on2);

        Light light3 = new Light(false);
        boolean on3 = light3.isOn();
        System.out.println("灭了的灯亮着吗：" + on3);
    }
}
