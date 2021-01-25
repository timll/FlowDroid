package soot.jimple.infoflow.test;

import soot.jimple.infoflow.test.android.ConnectionManager;
import soot.jimple.infoflow.test.android.TelephonyManager;

public class AliasTestCode {
    class A {
        int i;
        String str;

        String getStrA() {
            return this.str;
        }
    }
    class B extends A {
        String getStrB() {
            return this.str;
        }
    }
    class C {
        String str;
    }

    public void testAliasBefore1() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        b = a;
        b.i = TelephonyManager.getIMEI(); // {b.i} -> leak
        cm.publish(b.i); // {b.i}
    }
    public void testAliasBefore2() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        a = b; // {b.i} -> missed alias {_a.i}
        a.i = TelephonyManager.getIMEI(); // {b.i} | _{a.i} -> turn around as a.i lhs {a.i} -> leak
        cm.publish(b.i); // {b.i}
    }

    public void testAliasAfterPositive() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        a.i = TelephonyManager.getIMEI(); // {a.i} -> leak
        b = a; // {a.i}
        cm.publish(b.i); // {b.i}
    }
    public void testAliasAfterNegative() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        b.i = TelephonyManager.getIMEI(); // {a.i}
        b = a; // {a.i}
        cm.publish(b.i); // {b.i}
    }

    public void testAliasPrimitiveFieldPositive() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        a.i = TelephonyManager.getIMEI(); // {a.i}
        b.i = a.i; // {a.i}
        cm.publish(b.i); // {b.i}
    }
    public void testAliasPrimitiveFieldNegative() {
        ConnectionManager cm = new ConnectionManager();
        A a = new A();
        A b = new A();
        b.i = a.i; // {a.i}
        a.i = TelephonyManager.getIMEI(); // {b.i}
        cm.publish(b.i); // {b.i}
    }

    public void testAliasTwoSinksOverwrite() {
        ConnectionManager cm = new ConnectionManager(); // {b.i}
        A a = new A();
        A b = new A();
        a.i = TelephonyManager.getIMEI(); // {a.i, a.i'} StrongUpdate kills a.i, a.i' -> leak
        b.i = a.i; // {a.i, a.i'}
        cm.publish(b.i); // {b.i, b.i'}
        a.i = 0; // {b.i}
        cm.publish(b.i); // {b.i}
    }

    public void testAliasTwoSinksSourceAfter() {
        ConnectionManager cm = new ConnectionManager(); // {b.i}
        A a = new A();
        A b = new A();
        b = a; // {a.i, a.i'}
        cm.publish(b.i); // {b.i, b.i'}
        a.i = TelephonyManager.getIMEI(); // {b.i}
        cm.publish(b.i); // {b.i}
    }

    public void aliasCast() {
        ConnectionManager cm = new ConnectionManager();
        String tainted = TelephonyManager.getDeviceId();
        A a = new A();
        a.str = tainted;

        Object o = (Object) a;
        C c = (C) o;
        cm.publish(c.str);
    }

    public void aliasInstanceOf() {
        ConnectionManager cm = new ConnectionManager();
        String tainted = TelephonyManager.getDeviceId();
        A a;
        if (tainted.startsWith("x"))
            a = new A();
        else
            a = new B();
        a.str = tainted;

        if (a instanceof A)
            cm.publish(a.getStrA());
        else if (a instanceof B)
            cm.publish(((B) a).getStrB());
    }
}