(ns leiningen.new.cljs-tui
  (:require [clojure.java.io :as io]
            [leiningen.new.templates :refer [->files
                                             date
                                             multi-segment
                                             name-to-path
                                             project-name
                                             renderer
                                             sanitize-ns
                                             year]]
            [leiningen.core.main :as main]))

(def render (renderer "cljs-tui"))

(defn mark-last
  [deps]
  "Marks the last dependency as :last true for complex dependency rendering.
  Takes a vector of dep maps.
  Returns a vector of dep maps."
  (assoc-in deps [(dec (count deps)) :last] true))

(defn format-deps
  [deps]
  "Create a collection of dep maps for more complex rendering.
  Takes a list of vector pairs [name-sym \"version\"].
  Returns a single vector list."
  (->> deps
       (map (fn format-dep
              [[name version & {:keys [exclusions]}]]
              {:name name
               :version version
               :exclusions? (some? exclusions)
               :exclusions (when exclusions
                             (format-deps exclusions))}))
       (into [])
       (mark-last)))

(def clj-deps (format-deps
               '[[org.clojure/clojure       "1.10.0"]
                 [org.clojure/clojurescript "1.10.516"]
                 [org.clojure/tools.cli     "0.4.1"]
                 [mount                     "0.1.15"]
                 [reagent                   "0.8.1" :exclusions [[cljsjs/react]
                                                                 [cljsjs/react-dom]
                                                                 [cljsjs/create-react-class]]]
                 [re-frame                  "0.10.6"]]))

(def cljs-deps {:common (format-deps
                         '[[blessed                   "0.1.81"]
                           [react-blessed             "0.5.0"]
                           [react                     "16.7.0"]
                           [react-dom                 "16.7.0"]
                           [create-react-class        "15.6.3"]])
                :shadow (format-deps
                         '[[shadow-cljs               "2.7.24"]
                           [source-map-support        "0.5.10"]])
                :dev    (format-deps
                         '[[loose-envify              "1.4.0"]
                           [ws                        "6.1.2"]])})

(def files {:common          [[".gitignore"                               "gitignore"]
                              [".hgignore"                                "hgignore"]
                              ["bin/{{name}}"                             "bin.js"]
                              ["CHANGELOG.md"                             "CHANGELOG.md"]
                              ["docs/intro.md"                            "docs/intro.md"]
                              ["env/dev/{{nested-dirs}}/app.cljs"         "env/dev/app.cljs"]
                              ["env/dev/{{nested-dirs}}/debug/views.cljs" "env/dev/debug/views.cljs"]
                              [".npmignore"                               "npmignore"]
                              ["package.json"                             "package.json"]
                              ["LICENSE"                                  "LICENSE"]
                              ["README.md"                                "README.md"]
                              ["scripts/build"                            "scripts/build"]
                              ["src/{{nested-dirs}}/core.cljs"            "src/core.cljs"]
                              ["src/{{nested-dirs}}/demo/views.cljs"      "src/demo/views.cljs"]
                              ["src/{{nested-dirs}}/events.cljs"          "src/events.cljs"]
                              ["src/{{nested-dirs}}/keys.cljs"            "src/keys.cljs"]
                              ["src/{{nested-dirs}}/main.cljs"            "src/main.cljs"]
                              ["src/{{nested-dirs}}/resize.cljs"          "src/resize.cljs"]
                              ["src/{{nested-dirs}}/subs.cljs"            "src/subs.cljs"]
                              ["src/{{nested-dirs}}/views.cljs"           "src/views.cljs"]
                              ["test/{{nested-dirs}}/core_test.cljs"      "test/core_test.cljs"]]

            "+lein-figwheel" [["env/dev/user.clj"                      "lein-figwheel/env/dev/user.clj"]
                              ["project.clj"                           "project.clj"]
                              ["test/{{nested-dirs}}/test_runner.cljs" "lein-figwheel/test/test_runner.cljs"]]

            "+figwheel-main" [["env/dev/user.clj"                      "figwheel-main/env/dev/user.clj"]
                              ["dev.cljs.edn"                          "figwheel-main/dev.cljs.edn"]
                              ["figwheel-main.edn"                     "figwheel-main/figwheel-main.edn"]
                              ["prod.cljs.edn"                         "figwheel-main/prod.cljs.edn"]
                              ["project.clj"                           "project.clj"]
                              ["test.cljs.edn"                         "figwheel-main/test.cljs.edn"]
                              ["test/{{nested-dirs}}/test_runner.cljs" "figwheel-main/test/test_runner.cljs"]]

            "+shadow"        [["env/dev/user.clj" "shadow/env/dev/user.clj"]
                              ["shadow-cljs.edn"  "shadow/shadow-cljs.edn"]]})

(def opts (set (keys files)))

(defn select-files
  "Create list of files to render from this template based on build tool.
  Takes a list of string arguments
  Returns a list of vectors [\"dest\" \"source\"]"
  [args]
  (->> args
       (select-keys files)
       (vals)
       (reduce into (get files :common))))

(defn render-template
  "Use the leiningen render function to resolve the template variables.
  Takes map of data and a vector of destination and template source file.
  Returns nil."
  [data [dest template]]
  [dest (render template data)])

(intern 'leiningen.new.templates '*dir*)
(defn make-executable
  "Make a file in the template target dir executable.
  Takes a file to change.
  Returns nil."
  [file]
  (-> (str leiningen.new.templates/*dir* "/" file)
      (io/file)
      (.setExecutable true)))

(defn create-files
  "Generate all the template source, config, and script files.
  Takes the name of the project and a list of args.
  Returns nil."
  [name args]
  (let [render (renderer "cljs-tui")
        main-ns (sanitize-ns name)
        type (first args)
        data {:raw-name name
              :name (project-name name)
              :namespace (multi-segment main-ns)
              :main-ns main-ns
              :nested-dirs (name-to-path main-ns)
              :year (year)
              :date (date)
              :lein-figwheel? (= type "+lein-figwheel")
              :figwheel-main? (= type "+figwheel-main")
              :shadow?        (= type "+shadow")
              :clj-deps clj-deps
              :cljs-deps cljs-deps}]
    (main/info "Generating fresh 'lein new' cljs-tui project.")
    (->> args
         (mapcat #(get files %))
         (into (get files :common))
         (map #(render-template data %))
         (apply ->files data))
    (main/info "Updating script permissions.")
    (make-executable "scripts/build")
    (make-executable (str "bin/" (:name data)))))

(defn args-valid?
  "Predicate to return true when each arg is a valid option we support"
  [args]
  (and (every? opts args)
       (= (count (filter opts args)) 1)))

(defn help
  []
  (println "USAGE: lein new cljs-tui [args...]")
  (println "")
  (println   "  Args can be one of the following:

    +lein-figwheel
        Generate a ClojureScript CLI template using lein-fighwheel.
        https://github.com/bhauman/lein-figwheel

    +figwheel-main
        Generate a ClojureScript CLI template using figwheel-main.
        https://figwheel.org/

    +shadow
        Generate a ClojureScript CLI template using shadow-cljs
        http://shadow-cljs.org/"))

(defn display-help
  []
  (help)
  (System/exit 0))

(defn display-error
  [args]
  (println "Invalid arguments to new cljs-tui template.")
  (println "Received:" (clojure.string/join " " args))
  (println "")
  (help)
  (System/exit 1))

(defn cljs-tui
  "Generate a ClojureScript terminal-user-interface application template.
  Takes an optional build tool like +shadow, +figwheel-main, or +lein-figwheel."
  ([name]
   (create-files name ["+shadow"]))
  ([name & args]
   (if (args-valid? args)
     (create-files name args)
     (display-error args))))

(comment
 (select-files [])
 (select-files ["+bad"])
 (select-files ["+lein-figwheel"])
 (args-valid? ["+lein-figwheel"])
 (args-valid? ["+figwheel-main"])
 (args-valid? ["+shadow"])
 (args-valid? [])
 (args-valid? ["+lein-figwheel" "+figwheel-main" "+shadow"])
 (args-valid? ["+false" "+lein-figwheel"])
 (help))
