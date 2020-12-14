package net.czela.trello

class Helper {
    static Properties props;

    static Properties openProps(String propFile = "netadmin.properties") {
        props = new Properties();
        File f = new File(propFile);
        if (f.exists()) {
            props.load(new FileReader(f));
        } else {
            File f2 = new File('../../../'+propFile); // workaround when run a script from IDE from directory src/main/groovy
            if (f2.exists()) {
                props.load(new FileReader(f2));
            } else {
                assert false, "Error: File ${f.getAbsolutePath()} does not exist"
            }
        }
        return props
    }

    static String get(String label) {
        if (props == null) { // try load props from properties file
            openProps();
        }
        String v = props.getProperty(label)?.trim()
        assert v != null, "Error: Unknown property $label"
        return v
    }

    static List<String> getList(String prefix) {
        if (props == null) { // try load props from properties file
            openProps();
        }
        int i = 1
        int err = 0
        def result = []
        while(err < 3) {
            String v = props.getProperty("${prefix}$i")?.trim()
            if (v != null) {
                result.add(v)
            } else {
                err++
            }
            i++
        }
        return result
    }

    static Map<String,String> getMap(String prefix) {
        if (props == null) { // try load props from properties file
            openProps();
        }
        int i = 1
        int err = 0
        def result = [:]
        while(err < 3) {
            String k = null, v = null
            String kv= props.getProperty("${prefix}$i")?.trim()
            if (kv != null) {
                String[] arr = kv.split(/;/)
                k = arr[0]?.trim()
                v = arr[1]?.trim()
            }

            if (k != null && v != null) {
                result.put(k,v)
            } else {
                err++
            }
            i++
        }
        return result
    }
}
