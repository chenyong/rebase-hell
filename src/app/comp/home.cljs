
(ns app.comp.home
  (:require [hsl.core :refer [hsl]]
            [respo-ui.core :as ui]
            [respo.comp.space :refer [=<]]
            [respo.core :refer [defcomp <> >> list-> >> span div button pre a]]
            [app.config :as config]
            [feather.core :refer [comp-i]]
            [clojure.string :as string]
            [respo-alerts.core :refer [comp-prompt comp-select use-prompt]]
            [feather.core :refer [comp-icon]]
            [copy-text-to-clipboard :as copy!]
            [app.style :as style]
            [app.util.string :refer [default-branch?]])
  (:require-macros [clojure.core.strint :refer [<<]]))

(defcomp
 comp-branch
 (branch current remote?)
 (div
  {:class-name "hoverable",
   :style (merge
           ui/row-parted
           {:cursor :pointer, :line-height "32px", :padding "0 8px", :min-width 200}
           (if (= current branch) {:background-color (hsl 0 0 93)})
           (if remote? {:color (hsl 0 0 80), :line-height "26px", :font-size 13})),
   :on-click (fn [e d! m!]
     (if remote?
       (d! :effect/switch-remote-branch (last (string/split branch "/")))
       (d! :effect/switch-branch branch)))}
  (<> branch)
  (if (and (not= current branch) (not= (default-branch? branch)) (not remote?))
    (a
     {:on-click (fn [e d! m!] (d! :effect/remove-branch branch))}
     (comp-i :x 14 (hsl 20 80 80))))))

(defn render-button [text danger? on-click]
  (button
   {:style (merge
            style/button
            {:margin "4px 4px"}
            (if danger? {:color :red, :border-color :red})),
    :inner-text text,
    :on-click on-click}))

(defcomp
 comp-commit
 (states current)
 (comp-prompt
  (>> states :prompt)
  {:trigger (render-button "Commit" false nil),
   :initial (let [prefix (re-find (re-pattern "\\w+-\\d+(?=-)") current)]
     (if (string/blank? prefix) "" (<< "~{prefix} "))),
   :text "Commit message",
   :style-trigger {:margin "0 8px", :display :inline-block}}
  (fn [result d! m!] (if (not (string/blank? result)) (d! :effect/commit result)))))

(defcomp
 comp-footprints
 (footprints current)
 (list->
  {:style {:max-width 280}}
  (->> footprints
       (remove (fn [[k v]] (= v current)))
       (sort-by (fn [[k v]] v))
       (map
        (fn [[k v]]
          [k
           (div
            {:style (merge
                     ui/row-parted
                     {:line-height "1.4em",
                      :padding "6px 6px",
                      :font-size 13,
                      :overflow :hidden,
                      :cursor :pointer}),
             :class-name "hoverable",
             :on-click (fn [e d!] (d! :effect/switch-path k)),
             :title k}
            (<> v ui/expand)
            (span
             {:class-name "close-icon"}
             (comp-icon
              :x
              {:font-size 14, :color (hsl 0 90 70)}
              (fn [e d!] (d! :session/drop-footprint k)))))])))))

(def style-log
  {:line-height "20px",
   :font-size 13,
   :border (<< "1px solid ~(hsl 0 0 92)"),
   :padding "12px",
   :overflow :auto,
   :font-family ui/font-code,
   :background-color (hsl 260 10 96),
   :color (hsl 0 0 40),
   :white-space :pre-line})

(defcomp
 comp-log-chunk
 (log)
 (let [urls (re-seq (re-pattern "https?://\\S+") (:text log))]
   (div
    {:style {:position :relative}}
    (pre {:style (merge style-log {:margin-bottom 4}), :inner-text (:text log)})
    (if-not (empty? urls)
      (list->
       {}
       (->> urls
            (map
             (fn [url]
               [url
                (a
                 {:href url,
                  :inner-text url,
                  :target "_blank",
                  :style (merge ui/link {:line-height "16px", :height "16px"})})])))))
    (if (= :command (:kind log))
      (div
       {:class-name "clickable", :style {:position :absolute, :top 12, :right 12}}
       (comp-icon
        :copy
        {:font-size 16, :color (hsl 200 80 64), :cursor :pointer}
        (fn [e d! m!] (copy! (:text log)))))))))

(defcomp
 comp-logs
 (logs status)
 (div
  {:style (merge ui/flex ui/column)}
  (div
   {:style (merge ui/row-middle {:height 32})}
   (<> "Logs")
   (=< 16 nil)
   (button
    {:style style/button,
     :inner-text "Status",
     :on-click (fn [e d! m!] (d! :effect/status nil))})
   (=< 16 nil)
   (if (not (empty? logs))
     (button
      {:style (merge style/button),
       :inner-text "Clear",
       :on-click (fn [e d! m!] (d! :process/clear-logs nil))})))
  (if (not (empty? status))
    (div
     {:style (merge ui/row-middle {:padding 16})}
     (span {:class-name "rotating"} (comp-i :loader 24 (hsl 0 0 0 0.5)))
     (=< 16 nil)
     (list->
      {:style ui/expand}
      (->> status
           (map
            (fn [[pid command]]
              [pid
               (div
                {:style (merge
                         ui/row
                         {:font-family ui/font-code, :font-size 13, :line-height "20px"})}
                (<> pid {:color (hsl 0 0 80)})
                (=< 4 nil)
                (comp-icon
                 :x
                 {:font-size 12, :color (hsl 0 80 88), :cursor :pointer, :margin-top 4}
                 (fn [e d!] (d! :effect/kill-process pid)))
                (=< 12 nil)
                (div {:style ui/expand} (<> command)))]))))))
  (list->
   {:style (merge ui/flex {:overflow :auto})}
   (->> logs
        (sort-by (fn [[id log]] (unchecked-negate (:time log))))
        (map (fn [[id log]] [id (comp-log-chunk log)]))))))

(defcomp
 comp-new-branch
 (states code)
 (comp-prompt
  (>> states :new-branch)
  {:trigger (render-button "New Branch" false nil),
   :initial (if (string/blank? code) "JM-" (str code "-")),
   :text "Branch name",
   :style-trigger {:margin "0 8px", :display :inline-block},
   :validator (fn [x]
     (if (string/includes? x " ") "text with blanks is not a branch name!"))}
  (fn [result d! m!] (if (not (string/blank? result)) (d! :effect/new-branch result)))))

(defcomp
 comp-title
 (title)
 (div {:style {:font-family ui/font-fancy, :margin "8px 0 4px 0"}} (<> title)))

(defcomp
 comp-operations
 (states repo)
 (div
  {:style (merge ui/flex ui/column {:background-color (hsl 0 0 97), :padding 8})}
  (if (default-branch? (:current repo))
    (div
     {}
     (comp-title "Basic")
     (div
      {:style ui/row}
      (render-button "Pull" false (fn [e d! m!] (d! :effect/pull-current nil))))
     (comp-title "Others")
     (div {:style ui/row} (comp-new-branch (>> states :branch) (:code repo))))
    (div
     {}
     (comp-title "Basic")
     (div
      {}
      (render-button "Push" false (fn [e d! m!] (d! :effect/push-current nil)))
      (render-button "Pull" false (fn [e d! m!] (d! :effect/pull-current nil)))
      (render-button "Finish" false (fn [e d! m!] (d! :effect/finish-branch nil))))
     (comp-title "Other")
     (div
      {}
      (comp-new-branch (>> states :branch) (:code repo))
      (comp-commit (>> states :commit) (:current repo)))
     (comp-title "Forced")
     (div
      {}
      (render-button "Rebase master" true (fn [e d! m!] (d! :effect/rebase-master nil)))
      (render-button "Force push" true (fn [e d! m!] (d! :effect/force-push nil))))))))

(defcomp
 comp-quick-ops
 (states)
 (let [tag-plugin (use-prompt
                   (>> states :tag)
                   {:initial "",
                    :text "New tag version:",
                    :style {:vertical-align :middle},
                    :input-style {:font-family ui/font-code},
                    :placeholder "x.x.x or x.x.x-yx...",
                    :button-text "提交 tag"})]
   (div
    {}
    (a
     {:style {:cursor :pointer},
      :inner-text "Branches",
      :on-click (fn [e d! m!] (d! :effect/read-branches nil))})
    (=< 16 nil)
    (button
     {:style style/button,
      :inner-text "Fetch",
      :on-click (fn [e d! m!] (d! :effect/fetch-origin nil))})
    (=< 16 nil)
    (comp-prompt
     (>> states :pick-branch)
     {:trigger (button {:style (merge style/button), :inner-text "Pick issues"}),
      :initial "",
      :text "需要 pick 的若干 GitHub issue id",
      :style {:vertical-align :middle},
      :placeholder "100 or \"100, 101\"",
      :button-text "生成命令"}
     (fn [result d! m!]
       (if-not (string/blank? result)
         (let [issue-ids (->> (string/split result #"(\s|\,)+")
                              (filter (fn [x] (re-matches #"\d+" x)))
                              (map (fn [x] (println x) x))
                              (map js/parseInt)
                              (sort))]
           (d! :effect/pick-prs issue-ids)))))
    (=< 16 nil)
    (button
     {:inner-text "Stash",
      :style style/button,
      :on-click (fn [e d! m!] (d! :effect/stash nil))})
    (=< 16 nil)
    (button
     {:inner-text "Stash Apply",
      :style style/button,
      :on-click (fn [e d! m!] (d! :effect/stash-apply nil))})
    (=< 16 nil)
    (button
     {:style (merge style/button),
      :inner-text "Tag",
      :on-click (fn [e d!]
        (d! :effect/show-version nil)
        ((:show tag-plugin)
         d!
         (fn [result]
           (if-not (string/blank? result)
             (let [tag (string/trim result)]
               (when-not (string/blank? tag) (d! :effect/add-tag tag)))))))})
    (:ui tag-plugin))))

(defcomp
 comp-home
 (states repo logs status footprints)
 (div
  {:style (merge ui/row ui/flex {:padding 16, :overflow :auto})}
  (div
   {:style (merge ui/flex ui/column)}
   (comp-quick-ops (>> states :quick-ops))
   (=< nil 16)
   (div
    {:style (merge ui/flex ui/row)}
    (div
     {:style (merge ui/column {:background-color (hsl 0 0 97)})}
     (list->
      {}
      (->> (:branches repo)
           (sort)
           (map (fn [branch] [branch (comp-branch branch (:current repo) false)]))))
     (=< nil 16)
     (let [remote-branches (->> (:remote-branches repo)
                                (filter
                                 (fn [branch-path]
                                   (let [short-name (last (string/split branch-path "/"))]
                                     (and (not (contains? (:branches repo) short-name))
                                          (not= short-name "HEAD")))))
                                (sort)
                                (map (fn [branch] {:value branch, :display branch})))]
       (div
        {:style {:padding 8}}
        (comp-select
         (>> states :remote)
         nil
         remote-branches
         {:placeholder "Remote branches", :text "Checkout remote branch"}
         (fn [result d! m!]
           (if (some? result)
             (do (d! :effect/switch-remote-branch (last (string/split result "/")))))))))
     (div {:style ui/expand})
     (comp-footprints footprints (:upstream repo)))
    (=< 16 nil)
    (comp-operations (>> states :operations) repo)))
  (=< 16 nil)
  (comp-logs logs status)))
