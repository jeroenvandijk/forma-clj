(defproject forma/forma "0.2.0-SNAPSHOT"
  :description "[FORMA](http://goo.gl/4YTBw) gone Functional."
  :repositories {"conjars" "http://conjars.org/repo/"}
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :java-source-paths ["src/jvm"]
  :marginalia {:javascript ["mathjax/MathJax.js"]}
  :resources-paths ["resources"]
  :dev-resources-paths ["dev"]
  :jvm-opts ["-XX:MaxPermSize=128M"
             "-XX:+UseConcMarkSweepGC"
             "-Xms1024M" "-Xmx1048M" "-server"]
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.1.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clojure-csv/clojure-csv "1.3.2"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [incanter/incanter-core "1.3.0"]
                 [clj-time "0.3.4"]
                 [forma/gdal "1.8.0"]
                 [forma/jblas "1.2.1"]
                 [cascalog "1.9.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [cascalog-checkpoint "0.1.1"]
                 [backtype/dfs-datastores "1.1.3"]
                 [backtype/dfs-datastores-cascading "1.2.0"]
                 [org.apache.thrift/libthrift "0.8.0"
                  :exclusions [org.slf4j/slf4j-api]]]
  :aot [forma.hadoop.pail, forma.schema, #"forma.hadoop.jobs.*"]
  :profiles {:dev {:dependencies [[org.apache.hadoop/hadoop-core "0.20.2-dev"]
                                  [eightysteele/midje-cascalog "0.5.0"]
                                  [incanter/incanter-charts "1.3.0"]]
                   :plugins [[lein-swank "1.4.4"]
                             [lein-midje "2.0.0-SNAPSHOT"]
                             [lein-emr "0.1.0-SNAPSHOT"]]}})
