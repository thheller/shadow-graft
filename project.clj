(defproject com.thheller/shadow-graft "0.7.0"
  :description "Bridging function calls from CLJ to CLJS via HTML"
  :url "https://github.com/thheller/shadow-graft"

  :license
  {:name "Eclipse Public License"
   :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories
  {"clojars" {:url "https://clojars.org/repo"
              :sign-releases false}}

  :dependencies
  [[org.clojure/clojure "1.11.1" :scope "provided"]
   [org.clojure/clojurescript "1.11.60" :scope "provided"]]

  :source-paths
  ["src/dev"
   "src/main"])
