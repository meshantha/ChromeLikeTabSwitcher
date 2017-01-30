/*
 * Copyright 2016 Michael Rapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.mrapp.android.tabswitcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.Toolbar.OnMenuItemClickListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import de.mrapp.android.tabswitcher.gesture.DragHelper;
import de.mrapp.android.tabswitcher.view.TabSwitcherButton;
import de.mrapp.android.util.DisplayUtil.Orientation;
import de.mrapp.android.util.ThemeUtil;
import de.mrapp.android.util.ViewUtil;

import static de.mrapp.android.util.Condition.ensureAtLeast;
import static de.mrapp.android.util.Condition.ensureNotNull;
import static de.mrapp.android.util.DisplayUtil.getOrientation;

/**
 * A chrome-like tab switcher.
 *
 * @author Michael Rapp
 * @since 1.0.0
 */
public class TabSwitcher extends FrameLayout implements ViewTreeObserver.OnGlobalLayoutListener {

    /**
     * Defines the interface, a class, which should be notified about a tab switcher's events, must
     * implement.
     */
    public interface Listener {

        /**
         * The method, which is invoked, when the tab switcher has been shown.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         */
        void onSwitcherShown(@NonNull final TabSwitcher tabSwitcher);

        /**
         * The method, which is invoked, when the tab switcher has been hidden.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         */
        void onSwitcherHidden(@NonNull final TabSwitcher tabSwitcher);

        /**
         * The method, which is invoked, when the currently selected tab has been changed.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         * @param selectedTabIndex
         *         The index of the currently selected tab as an {@link Integer} value or -1, if the
         *         tab switcher does not contain any tabs
         * @param selectedTab
         *         The currently selected tab as an instance of the class {@link Tab} or null, if
         *         the tab switcher does not contain any tabs
         */
        void onSelectionChanged(@NonNull final TabSwitcher tabSwitcher, int selectedTabIndex,
                                @Nullable Tab selectedTab);

        /**
         * The method, which is invoked, when a tab has been added to the tab switcher.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         * @param index
         *         The index of the tab, which has been added, as an {@link Integer} value
         * @param tab
         *         The tab, which has been added, as an instance of the class {@link Tab}. The tab
         *         may not be null
         */
        void onTabAdded(@NonNull final TabSwitcher tabSwitcher, int index, @NonNull Tab tab);

        /**
         * The method, which is invoked, when a tab has been removed from the tab switcher.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         * @param index
         *         The index of the tab, which has been removed, as an {@link Integer} value
         * @param tab
         *         The tab, which has been removed, as an instance of the class {@link Tab}. The tab
         *         may not be null
         */
        void onTabRemoved(@NonNull final TabSwitcher tabSwitcher, int index, @NonNull Tab tab);

        /**
         * The method, which is invoked, when all tabs have been removed from the tab switcher.
         *
         * @param tabSwitcher
         *         The observed tab switcher as an instance of the class {@link TabSwitcher}. The
         *         tab switcher may not be null
         */
        void onAllTabsRemoved(@NonNull final TabSwitcher tabSwitcher);

    }

    public enum AnimationType {

        SWIPE_LEFT,

        SWIPE_RIGHT

    }

    public abstract static class Decorator {

        public int getViewType(@NonNull final Tab tab) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }

        @NonNull
        public abstract View onInflateView(@NonNull final LayoutInflater inflater,
                                           @NonNull final ViewGroup parent, final int viewType);

        public abstract void onShowTab(@NonNull final Context context,
                                       @NonNull final TabSwitcher tabSwitcher,
                                       @NonNull final View view, @NonNull final Tab tab,
                                       final int viewType);

    }

    private class TabView {

        private int index;

        private View view;

        private Tag tag;

        private ViewHolder viewHolder;

        public TabView(final int index, @NonNull final View view) {
            ensureAtLeast(index, 0, "The index must be at least 0");
            ensureNotNull(view, "The view may not be null");
            this.index = index;
            this.view = view;
            this.viewHolder = (ViewHolder) view.getTag(R.id.tag_view_holder);
            this.tag = (Tag) view.getTag(R.id.tag_properties);

            if (this.tag == null) {
                this.tag = new Tag();
                this.view.setTag(R.id.tag_properties, this.tag);
            }
        }

    }

    private class Iterator implements java.util.Iterator<TabView> {

        private boolean reverse;

        private int index;

        private int end;

        private TabView current;

        private TabView previous;

        private TabView first;

        public Iterator() {
            this(false);
        }

        public Iterator(final boolean reverse) {
            this(reverse, -1);
        }

        public Iterator(final boolean reverse, final int start) {
            this(reverse, start, -1);
        }

        public Iterator(final boolean reverse, final int start, final int end) {
            this.reverse = reverse;
            this.end = end != -1 ? (reverse ? end - 1 : end + 1) : -1;
            this.previous = null;
            this.index = start != -1 ? start : (reverse ? tabContainer.getChildCount() - 1 : 0);
            int previousIndex = reverse ? this.index + 1 : this.index - 1;

            if (previousIndex >= 0 && previousIndex < tabContainer.getChildCount()) {
                this.current = new TabView(previousIndex,
                        tabContainer.getChildAt(tabContainer.getChildCount() - previousIndex - 1));
            } else {
                this.current = null;
            }
        }

        public TabView first() {
            return first;
        }

        public TabView previous() {
            return previous;
        }

        public TabView peek() {
            if (hasNext()) {
                View view = tabContainer.getChildAt(tabContainer.getChildCount() - index - 1);
                return new TabView(index, view);
            }

            return null;
        }

        @Override
        public boolean hasNext() {
            if (index == end) {
                return false;
            } else {
                if (reverse) {
                    return index >= 0;
                } else {
                    return tabContainer.getChildCount() - index >= 1;
                }
            }
        }

        @Override
        public TabView next() {
            if (hasNext()) {
                View view = tabContainer.getChildAt(tabContainer.getChildCount() - index - 1);
                previous = current;

                if (first == null) {
                    first = current;
                }

                current = new TabView(index, view);
                index += reverse ? -1 : 1;
                return current;
            }

            return null;
        }

    }

    private static class ViewHolder {

        private ViewGroup titleContainer;

        private TextView titleTextView;

        private ImageButton closeButton;

        private ViewGroup childContainer;

        private View child;

        private ImageView previewImageView;

        private View borderView;

    }

    private static class Tag implements Cloneable {

        private float projectedPosition;

        private float actualPosition;

        private float distance;

        private State state;

        private boolean closing;

        @Override
        public Tag clone() {
            Tag clone;

            try {
                clone = (Tag) super.clone();
            } catch (ClassCastException | CloneNotSupportedException e) {
                clone = new Tag();
            }

            clone.projectedPosition = projectedPosition;
            clone.actualPosition = actualPosition;
            clone.distance = distance;
            clone.state = state;
            clone.closing = closing;
            return clone;
        }

    }

    private enum State {

        STACKED_TOP,

        TOP_MOST_HIDDEN,

        TOP_MOST,

        VISIBLE,

        BOTTOM_MOST_HIDDEN,

        STACKED_BOTTOM

    }

    private enum ScrollDirection {

        NONE,

        DRAGGING_UP,

        DRAGGING_DOWN,

        OVERSHOOT_UP,

        OVERSHOOT_DOWN;

    }

    private enum Axis {

        DRAGGING_AXIS,

        ORTHOGONAL_AXIS

    }

    private class FlingAnimation extends Animation {

        private final float flingDistance;

        public FlingAnimation(final float flingDistance) {
            this.flingDistance = flingDistance;
        }

        @Override
        protected void applyTransformation(final float interpolatedTime, final Transformation t) {
            if (dragAnimation != null) {
                handleDrag(flingDistance * interpolatedTime, 0);
            }
        }

    }

    private class OvershootUpAnimation extends Animation {

        private Float startPosition = null;

        @SuppressWarnings("WrongConstant")
        @Override
        protected void applyTransformation(final float interpolatedTime, final Transformation t) {
            if (overshootUpAnimation != null) {
                Iterator iterator = new Iterator();
                TabView tabView;

                while ((tabView = iterator.next()) != null) {
                    View view = tabView.view;

                    if (tabView.index == 0) {
                        if (startPosition == null) {
                            startPosition = getPosition(Axis.DRAGGING_AXIS, view);
                        }

                        float targetPosition = tabView.tag.projectedPosition;
                        setPosition(Axis.DRAGGING_AXIS, view, startPosition +
                                (targetPosition - startPosition) * interpolatedTime);
                    } else {
                        View firstView = iterator.first().view;
                        view.setVisibility(getPosition(Axis.DRAGGING_AXIS, firstView) <=
                                getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
                                getVisibility(tabView));
                    }
                }
            }
        }

    }

    private static final int STACKED_TAB_COUNT = 3;

    private static final float NON_LINEAR_DRAG_FACTOR = 0.5f;

    private static final float MAX_DOWN_OVERSHOOT_ANGLE = 3f;

    private static final float MAX_UP_OVERSHOOT_ANGLE = 2f;

    private int[] padding;

    private Toolbar toolbar;

    private ViewGroup tabContainer;

    private Set<Listener> listeners;

    private Decorator decorator;

    private Queue<Runnable> pendingActions;

    /**
     * A list, which contains the tab switcher's tabs.
     */
    private List<Tab> tabs;

    private int selectedTabIndex;

    private int tabBackgroundColor;

    private int dragThreshold;

    /**
     * An instance of the class {@link DragHelper}, which is used to recognize drag gestures.
     */
    private DragHelper dragHelper;

    private DragHelper overshootDragHelper;

    private DragHelper closeDragHelper;

    private VelocityTracker velocityTracker;

    private boolean switcherShown;

    private int stackedTabSpacing;

    private int minTabSpacing;

    private int maxTabSpacing;

    private int maxOvershootDistance;

    private float minFlingVelocity;

    private float maxFlingVelocity;

    private float minCloseFlingVelocity;

    private float closedTabAlpha;

    private float closedTabScale;

    private int tabInset;

    private int tabBorderWidth;

    private int tabTitleContainerHeight;

    private ScrollDirection scrollDirection;

    private TabView draggedTabView;

    private int lastAttachedIndex;

    private float attachedPosition;

    private float topDragThreshold = -Float.MIN_VALUE;

    private float bottomDragThreshold = Float.MAX_VALUE;

    private int pointerId = -1;

    private ViewPropertyAnimator showSwitcherAnimation;

    private ValueAnimator marginAnimation;

    private ViewPropertyAnimator hideSwitcherAnimation;

    private Animation dragAnimation;

    private ViewPropertyAnimator overshootAnimation;

    private Animation overshootUpAnimation;

    private ViewPropertyAnimator closeAnimation;

    private ViewPropertyAnimator relocateAnimation;

    private ViewPropertyAnimator toolbarAnimation;

    /**
     * Initializes the view.
     *
     * @param attributeSet
     *         The attribute set, which should be used to initialize the view, as an instance of the
     *         type {@link AttributeSet} or null, if no attributes should be obtained
     * @param defaultStyle
     *         The default style to apply to this view. If 0, no style will be applied (beyond what
     *         is included in the theme). This may either be an attribute resource, whose value will
     *         be retrieved from the current theme, or an explicit style resource
     * @param defaultStyleResource
     *         A resource identifier of a style resource that supplies default values for the view,
     *         used only if the default style is 0 or can not be found in the theme. Can be 0 to not
     *         look for defaults
     */
    private void initialize(@Nullable final AttributeSet attributeSet,
                            @AttrRes final int defaultStyle,
                            @StyleRes final int defaultStyleResource) {
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        padding = new int[]{0, 0, 0, 0};
        listeners = new LinkedHashSet<>();
        pendingActions = new LinkedList<>();
        tabs = new ArrayList<>();
        selectedTabIndex = -1;
        switcherShown = false;
        Resources resources = getResources();
        dragThreshold = resources.getDimensionPixelSize(R.dimen.drag_threshold);
        dragHelper = new DragHelper(dragThreshold);
        overshootDragHelper = new DragHelper(0);
        closeDragHelper =
                new DragHelper(resources.getDimensionPixelSize(R.dimen.close_drag_threshold));
        stackedTabSpacing = resources.getDimensionPixelSize(R.dimen.stacked_tab_spacing);
        minTabSpacing = resources.getDimensionPixelSize(R.dimen.min_tab_spacing);
        maxTabSpacing = resources.getDimensionPixelSize(R.dimen.max_tab_spacing);
        maxOvershootDistance = resources.getDimensionPixelSize(R.dimen.max_overshoot_distance);
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        minCloseFlingVelocity = resources.getDimensionPixelSize(R.dimen.min_close_fling_velocity);
        TypedValue typedValue = new TypedValue();
        resources.getValue(R.dimen.closed_tab_scale, typedValue, true);
        closedTabScale = typedValue.getFloat();
        resources.getValue(R.dimen.closed_tab_alpha, typedValue, true);
        closedTabAlpha = typedValue.getFloat();
        tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
        tabBorderWidth = resources.getDimensionPixelSize(R.dimen.tab_border_width);
        tabTitleContainerHeight =
                resources.getDimensionPixelSize(R.dimen.tab_title_container_height);
        scrollDirection = ScrollDirection.NONE;
        inflateLayout();
        obtainStyledAttributes(attributeSet, defaultStyle, defaultStyleResource);
    }

    private void inflateLayout() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        toolbar = (Toolbar) inflater.inflate(R.layout.tab_switcher_toolbar, this, false);
        toolbar.setVisibility(View.INVISIBLE);
        addView(toolbar, LayoutParams.MATCH_PARENT,
                ThemeUtil.getDimensionPixelSize(getContext(), R.attr.actionBarSize));
        tabContainer = new FrameLayout(getContext());
        addView(tabContainer, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    }

    private SparseArray<View> childViews;

    private ViewGroup inflateTabView(@NonNull final Tab tab) {
        int color = tab.getColor();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        ViewHolder viewHolder = new ViewHolder();
        ViewGroup tabView = (ViewGroup) inflater.inflate(R.layout.tab_view, this, false);
        Drawable backgroundDrawable =
                ContextCompat.getDrawable(getContext(), R.drawable.tab_background);
        backgroundDrawable
                .setColorFilter(color != -1 ? color : tabBackgroundColor, PorterDuff.Mode.MULTIPLY);
        ViewUtil.setBackground(tabView, backgroundDrawable);
        int padding = tabInset + tabBorderWidth;
        tabView.setPadding(padding, tabInset, padding, padding);
        viewHolder.titleContainer = (ViewGroup) tabView.findViewById(R.id.tab_title_container);
        viewHolder.titleTextView = (TextView) tabView.findViewById(R.id.tab_title_text_view);
        viewHolder.titleTextView.setText(tab.getTitle());
        viewHolder.titleTextView
                .setCompoundDrawablesWithIntrinsicBounds(tab.getIcon(getContext()), null, null,
                        null);
        viewHolder.closeButton = (ImageButton) tabView.findViewById(R.id.close_tab_button);
        viewHolder.closeButton.setVisibility(tab.isCloseable() ? View.VISIBLE : View.GONE);
        viewHolder.closeButton.setOnClickListener(createCloseButtonClickListener(tab));
        viewHolder.childContainer = (ViewGroup) tabView.findViewById(R.id.child_container);
        viewHolder.previewImageView = (ImageView) tabView.findViewById(R.id.preview_image_view);
        adaptChildAndPreviewMargins(viewHolder);
        viewHolder.borderView = tabView.findViewById(R.id.border_view);
        Drawable borderDrawable = ContextCompat.getDrawable(getContext(), R.drawable.tab_border);
        borderDrawable
                .setColorFilter(color != -1 ? color : tabBackgroundColor, PorterDuff.Mode.MULTIPLY);
        ViewUtil.setBackground(viewHolder.borderView, borderDrawable);
        tabView.setTag(R.id.tag_view_holder, viewHolder);
        return tabView;
    }

    private View inflateChildView(@NonNull final ViewGroup parent, final int viewType) {
        View child = null;

        if (childViews == null) {
            childViews = new SparseArray<>(getDecorator().getViewTypeCount());
        } else {
            child = childViews.get(viewType);
        }

        if (child == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            child = getDecorator().onInflateView(inflater, parent, viewType);
            childViews.put(viewType, child);
        }

        return child;
    }

    private void notifyOnSwitcherShown() {
        for (Listener listener : listeners) {
            listener.onSwitcherShown(this);
        }
    }

    private void notifyOnSwitcherHidden() {
        for (Listener listener : listeners) {
            listener.onSwitcherHidden(this);
        }
    }

    private void notifyOnSelectionChanged(final int selectedTabIndex,
                                          @Nullable final Tab selectedTab) {
        for (Listener listener : listeners) {
            listener.onSelectionChanged(this, selectedTabIndex, selectedTab);
        }
    }

    private void notifyOnTabAdded(final int index, @NonNull final Tab tab) {
        for (Listener listener : listeners) {
            listener.onTabAdded(this, index, tab);
        }
    }

    private void notifyOnTabRemoved(final int index, @NonNull final Tab tab) {
        for (Listener listener : listeners) {
            listener.onTabRemoved(this, index, tab);
        }
    }

    private void notifyOnAllTabsRemoved() {
        for (Listener listener : listeners) {
            listener.onAllTabsRemoved(this);
        }
    }

    private OnClickListener createCloseButtonClickListener(@NonNull final Tab tab) {
        return new OnClickListener() {

            @Override
            public void onClick(final View v) {
                removeTab(tab);
            }

        };
    }

    private void animateClose(@NonNull final TabView tabView, final boolean close,
                              final float flingVelocity, final long startDelay,
                              @Nullable final Animator.AnimatorListener listener) {
        View view = tabView.view;
        float scale = getScale(view, true);
        float closedTabPosition = calculateClosedTabPosition();
        float position = getPosition(Axis.ORTHOGONAL_AXIS, view);
        float targetPosition =
                close ? (position < 0 ? -1 * closedTabPosition : closedTabPosition) : 0;
        float distance = Math.abs(targetPosition - position);
        long animationDuration;

        if (flingVelocity >= minCloseFlingVelocity) {
            animationDuration = Math.round((distance / flingVelocity) * 1000);
        } else {
            animationDuration = Math.round(
                    getResources().getInteger(android.R.integer.config_longAnimTime) *
                            (distance / closedTabPosition));
        }

        closeAnimation = view.animate();
        closeAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        closeAnimation.setListener(listener);
        closeAnimation.setDuration(animationDuration);
        animatePosition(Axis.ORTHOGONAL_AXIS, closeAnimation, view, targetPosition, true);
        animateScale(Axis.ORTHOGONAL_AXIS, closeAnimation, close ? closedTabScale * scale : scale);
        animateScale(Axis.DRAGGING_AXIS, closeAnimation, close ? closedTabScale * scale : scale);
        closeAnimation.alpha(close ? closedTabAlpha : 1);
        closeAnimation.setStartDelay(startDelay);
        closeAnimation.start();
    }

    private Animator.AnimatorListener createCloseAnimationListener(
            @NonNull final TabView closedTabView, final boolean close) {
        return new AnimatorListenerAdapter() {

            private void adjustActualPositionOfStackedTabViews(final boolean reverse) {
                Iterator iterator = new Iterator(reverse, closedTabView.index);
                TabView tabView;
                Float previousActualPosition = null;

                while ((tabView = iterator.next()) != null) {
                    float actualPosition = tabView.tag.actualPosition;

                    if (previousActualPosition != null) {
                        tabView.tag.actualPosition = previousActualPosition;
                        applyTag(closedTabView);
                    }

                    previousActualPosition = actualPosition;
                }
            }

            private void relocateWhenStackedTabViewWasRemoved(final boolean top) {
                long startDelay = getResources().getInteger(android.R.integer.config_shortAnimTime);
                int start = closedTabView.index + (top ? -1 : 1);
                Iterator iterator = new Iterator(top, closedTabView.index);
                TabView tabView;
                Float previousProjectedPosition = null;

                while ((tabView = iterator.next()) != null &&
                        (tabView.tag.state == State.TOP_MOST_HIDDEN ||
                                tabView.tag.state == State.STACKED_TOP ||
                                tabView.tag.state == State.BOTTOM_MOST_HIDDEN ||
                                tabView.tag.state == State.STACKED_BOTTOM)) {
                    float projectedPosition = tabView.tag.projectedPosition;

                    if (previousProjectedPosition != null) {
                        if (tabView.tag.state == State.TOP_MOST_HIDDEN ||
                                tabView.tag.state == State.BOTTOM_MOST_HIDDEN) {
                            TabView previous = iterator.previous();
                            tabView.tag.state = previous.tag.state;

                            if (top) {
                                tabView.tag.projectedPosition = previousProjectedPosition;
                                long delay = (start + 1 - tabView.index) * startDelay;
                                animateRelocate(tabView, previousProjectedPosition, delay,
                                        createRelocateAnimationListener(tabView, null, true));
                            } else {
                                adaptVisibility(tabView);
                            }

                            break;
                        } else {
                            TabView peek = iterator.peek();
                            State peekState = peek != null ? peek.tag.state : null;
                            boolean reset = !iterator.hasNext() ||
                                    (peekState != State.STACKED_TOP &&
                                            peekState != State.STACKED_BOTTOM);
                            tabView.tag.projectedPosition = previousProjectedPosition;
                            long delay =
                                    (top ? (start + 1 - tabView.index) : (tabView.index - start)) *
                                            startDelay;
                            animateRelocate(tabView, previousProjectedPosition, delay,
                                    createRelocateAnimationListener(tabView, null, reset));
                        }
                    }

                    previousProjectedPosition = projectedPosition;
                }

                adjustActualPositionOfStackedTabViews(!top);
            }

            private void relocateWhenVisibleTabViewWasRemoved() {
                long startDelay = getResources().getInteger(android.R.integer.config_shortAnimTime);
                int start = closedTabView.index - 1;
                Iterator iterator = new Iterator(true, start);
                TabView tabView;
                int firstStackedTabIndex = -1;

                while ((tabView = iterator.next()) != null && firstStackedTabIndex == -1) {
                    if (tabView.tag.state == State.BOTTOM_MOST_HIDDEN ||
                            tabView.tag.state == State.STACKED_BOTTOM) {
                        firstStackedTabIndex = tabView.index;
                    }

                    TabView previous = iterator.previous();
                    boolean reset = !iterator.hasNext() || firstStackedTabIndex != -1;
                    Animator.AnimatorListener listener =
                            createRelocateAnimationListener(tabView, previous.tag, reset);
                    animateRelocate(tabView, previous.tag.projectedPosition,
                            (start + 1 - tabView.index) * startDelay, tabView.index == start ?
                                    createRelocateAnimationListenerWrapper(closedTabView,
                                            listener) : listener);
                }

                if (firstStackedTabIndex != -1) {
                    iterator = new Iterator(true, firstStackedTabIndex);
                    Float previousActualPosition = null;

                    while ((tabView = iterator.next()) != null) {
                        float actualPosition = tabView.tag.actualPosition;

                        if (previousActualPosition != null) {
                            tabView.tag.actualPosition = previousActualPosition;
                        }

                        previousActualPosition = actualPosition;
                    }
                }
            }

            private void animateRelocate(@NonNull final TabView tabView,
                                         final float relocatePosition, final long startDelay,
                                         @Nullable final Animator.AnimatorListener listener) {
                View view = tabView.view;
                relocateAnimation = view.animate();
                relocateAnimation.setListener(listener);
                relocateAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                relocateAnimation.setDuration(
                        getResources().getInteger(android.R.integer.config_mediumAnimTime));
                animatePosition(Axis.DRAGGING_AXIS, relocateAnimation, view, relocatePosition,
                        true);
                relocateAnimation.setStartDelay(startDelay);
                relocateAnimation.start();
            }

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);

                if (close) {
                    if (closedTabView.tag.state == State.BOTTOM_MOST_HIDDEN) {
                        adjustActualPositionOfStackedTabViews(true);
                    } else if (closedTabView.tag.state == State.TOP_MOST_HIDDEN) {
                        adjustActualPositionOfStackedTabViews(false);
                    } else if (closedTabView.tag.state == State.STACKED_BOTTOM) {
                        relocateWhenStackedTabViewWasRemoved(false);
                    } else if (closedTabView.tag.state == State.STACKED_TOP) {
                        relocateWhenStackedTabViewWasRemoved(true);
                    } else {
                        relocateWhenVisibleTabViewWasRemoved();
                    }
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                if (close) {
                    int index = closedTabView.index;
                    tabContainer.removeViewAt(getChildIndex(index));
                    Tab tab = tabs.remove(index);
                    notifyOnTabRemoved(index, tab);

                    if (isEmpty()) {
                        selectedTabIndex = -1;
                        notifyOnSelectionChanged(-1, null);
                        animateToolbarVisibility(isToolbarShown(), 0);
                    } else if (selectedTabIndex == closedTabView.index) {
                        if (selectedTabIndex > 0) {
                            selectedTabIndex--;
                        }

                        notifyOnSelectionChanged(selectedTabIndex, getTab(selectedTabIndex));
                    }
                } else {
                    View view = closedTabView.view;
                    adaptTopMostTabViewWhenClosingAborted(closedTabView, closedTabView.index + 1);
                    closedTabView.tag.closing = false;
                    setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
                    handleRelease(null);
                    animateToolbarVisibility(true, 0);
                }

                closeAnimation = null;
                draggedTabView = null;
                executePendingAction();
            }

        };
    }

    private float getDefaultPivot(@NonNull final Axis axis, @NonNull final View view) {
        if (axis == Axis.DRAGGING_AXIS) {
            return isDraggingHorizontally() ? getSize(axis, view) / 2f : 0;
        } else {
            return isDraggingHorizontally() ? 0 : getSize(axis, view) / 2f;
        }
    }

    private float getPivotWhenClosing(@NonNull final Axis axis, @NonNull final View view) {
        if (axis == Axis.DRAGGING_AXIS) {
            return maxTabSpacing;
        } else {
            return getDefaultPivot(axis, view);
        }
    }

    private float getPivotOnOvershootDown(@NonNull final Axis axis, @NonNull final View view) {
        if (axis == Axis.DRAGGING_AXIS) {
            return maxTabSpacing;
        } else {
            return getSize(axis, view) / 2f;
        }
    }

    private float getPivotOnOvershootUp(@NonNull final Axis axis, @NonNull final View view) {
        return getSize(axis, view) / 2f;
    }

    private Animator.AnimatorListener createRelocateAnimationListenerWrapper(
            @NonNull final TabView closedTabView,
            @Nullable final Animator.AnimatorListener listener) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);

                if (listener != null) {
                    listener.onAnimationStart(animation);
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                adaptTopMostTabViewWhenClosingAborted(closedTabView, closedTabView.index);

                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
            }

        };
    }

    private Animator.AnimatorListener createRelocateAnimationListener(
            @NonNull final TabView tabView, @Nullable final Tag tag, final boolean reset) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);
                View view = tabView.view;
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                if (tag != null) {
                    View view = tabView.view;
                    view.setTag(R.id.tag_properties, tag);
                    tabView.tag = tag;
                }

                applyTag(tabView);

                if (reset) {
                    for (int i = 0; i < tabContainer.getChildCount(); i++) {
                        View view = tabContainer.getChildAt(i);
                        System.out.println(view.getVisibility() == View.VISIBLE);
                    }

                    relocateAnimation = null;
                    executePendingAction();
                }
            }

        };
    }

    /**
     * Obtains all attributes froma specific attribute set.
     *
     * @param attributeSet
     *         The attribute set, the attributes should be obtained from, as an instance of the type
     *         {@link AttributeSet} or null, if no attributes should be obtained
     * @param defaultStyle
     *         The default style to apply to this view. If 0, no style will be applied (beyond what
     *         is included in the theme). This may either be an attribute resource, whose value will
     *         be retrieved from the current theme, or an explicit style resource
     * @param defaultStyleResource
     *         A resource identifier of a style resource that supplies default values for the view,
     *         used only if the default style is 0 or can not be found in the theme. Can be 0 to not
     *         look for defaults
     */
    private void obtainStyledAttributes(@Nullable final AttributeSet attributeSet,
                                        @AttrRes final int defaultStyle,
                                        @StyleRes final int defaultStyleResource) {
        TypedArray typedArray = getContext()
                .obtainStyledAttributes(attributeSet, R.styleable.TabSwitcher, defaultStyle,
                        defaultStyleResource);

        try {
            obtainBackground(typedArray);
            obtainTabBackgroundColor(typedArray);
        } finally {
            typedArray.recycle();
        }
    }

    /**
     * Obtains the view's background from a specific typed array.
     *
     * @param typedArray
     *         The typed array, the background should be obtained from, as an instance of the class
     *         {@link TypedArray}. The typed array may not be null
     */
    private void obtainBackground(@NonNull final TypedArray typedArray) {
        int resourceId = typedArray.getResourceId(R.styleable.TabSwitcher_android_background, 0);

        if (resourceId != 0) {
            ViewUtil.setBackground(this, ContextCompat.getDrawable(getContext(), resourceId));
        } else {
            int defaultValue =
                    ContextCompat.getColor(getContext(), R.color.tab_switcher_background_color);
            int color =
                    typedArray.getColor(R.styleable.TabSwitcher_android_background, defaultValue);
            setBackgroundColor(color);
        }
    }

    /**
     * Obtains the background color of tabs from a specific typed array.
     *
     * @param typedArray
     *         The typed array, the background color should be obtained from, as an instance of the
     *         class {@link TypedArray}. The typed array may not be null
     */
    private void obtainTabBackgroundColor(@NonNull final TypedArray typedArray) {
        int defaultValue = ContextCompat.getColor(getContext(), R.color.tab_background_color);
        tabBackgroundColor =
                typedArray.getColor(R.styleable.TabSwitcher_tabBackgroundColor, defaultValue);
    }

    private int getPadding(@NonNull final Axis axis, final int gravity) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            return gravity == Gravity.START ? getPaddingTop() : getPaddingBottom();
        } else {
            return gravity == Gravity.START ? getPaddingLeft() : getPaddingRight();
        }
    }

    private Axis getOrientationInvariantAxis(@NonNull final Axis axis) {
        if (isDraggingHorizontally()) {
            return axis == Axis.DRAGGING_AXIS ? Axis.ORTHOGONAL_AXIS : Axis.DRAGGING_AXIS;
        }

        return axis;
    }

    private boolean isDraggingHorizontally() {
        return getOrientation(getContext()) == Orientation.LANDSCAPE;
    }

    private float getScale(@NonNull final View view, final boolean includePadding) {
        LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
        float width = view.getWidth();
        float targetWidth = width + layoutParams.leftMargin + layoutParams.rightMargin -
                (includePadding ? getPaddingLeft() + getPaddingRight() : 0);
        return targetWidth / width;
    }

    private float getSize(@NonNull final Axis axis, @NonNull final View view) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            return view.getHeight() * getScale(view, false);
        } else {
            return view.getWidth() * getScale(view, false);
        }
    }

    private float getPosition(@NonNull final Axis axis, @NonNull final MotionEvent event) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            return event.getY();
        } else {
            return event.getX();
        }
    }

    private float getPosition(@NonNull final Axis axis, @NonNull final View view) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            return view.getY() -
                    (isToolbarShown() && isSwitcherShown() ? toolbar.getHeight() - tabInset : 0) -
                    getPadding(axis, Gravity.START);
        } else {
            LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
            return view.getX() - layoutParams.leftMargin - getPaddingLeft() / 2f +
                    getPaddingRight() / 2f;
        }
    }

    private void setPosition(@NonNull final Axis axis, @NonNull final View view,
                             final float position) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            view.setY((isToolbarShown() && isSwitcherShown() ? toolbar.getHeight() - tabInset : 0) +
                    getPadding(axis, Gravity.START) + position);
        } else {
            LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
            view.setX(position + layoutParams.leftMargin + getPaddingLeft() / 2f -
                    getPaddingRight() / 2f);
        }
    }

    private void animatePosition(@NonNull final Axis axis,
                                 @NonNull final ViewPropertyAnimator animator,
                                 @NonNull final View view, final float position,
                                 final boolean includePadding) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            animator.y(
                    (isToolbarShown() && isSwitcherShown() ? toolbar.getHeight() - tabInset : 0) +
                            (includePadding ? getPadding(axis, Gravity.START) : 0) + position);
        } else {
            LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
            animator.x(position + layoutParams.leftMargin +
                    (includePadding ? getPaddingLeft() / 2f - getPaddingRight() / 2f : 0));
        }
    }

    private float getRotation(@NonNull final Axis axis, @NonNull final View view) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            return view.getRotationY();
        } else {
            return view.getRotationX();
        }
    }

    private void setRotation(@NonNull final Axis axis, @NonNull final View view,
                             final float angle) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            view.setRotationY(isDraggingHorizontally() ? -1 * angle : angle);
        } else {
            view.setRotationX(isDraggingHorizontally() ? -1 * angle : angle);
        }
    }

    private void animateRotation(@NonNull final Axis axis,
                                 @NonNull final ViewPropertyAnimator animator, final float angle) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            animator.rotationY(isDraggingHorizontally() ? -1 * angle : angle);
        } else {
            animator.rotationX(isDraggingHorizontally() ? -1 * angle : angle);
        }
    }

    private void setScale(@NonNull final Axis axis, @NonNull final View view, final float scale) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            view.setScaleY(scale);
        } else {
            view.setScaleX(scale);
        }
    }

    private void animateScale(@NonNull final Axis axis,
                              @NonNull final ViewPropertyAnimator animator, final float scale) {
        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            animator.scaleY(scale);
        } else {
            animator.scaleX(scale);
        }
    }

    private void setPivot(@NonNull final Axis axis, @NonNull final View view, final float pivot) {
        LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();

        if (getOrientationInvariantAxis(axis) == Axis.DRAGGING_AXIS) {
            float newPivot = pivot - layoutParams.topMargin - tabTitleContainerHeight;
            view.setTranslationY(view.getTranslationY() +
                    (view.getPivotY() - newPivot) * (1 - view.getScaleY()));
            view.setPivotY(newPivot);
        } else {
            float newPivot = pivot - layoutParams.leftMargin;
            view.setTranslationX(view.getTranslationX() +
                    (view.getPivotX() - newPivot) * (1 - view.getScaleX()));
            view.setPivotX(newPivot);
        }
    }

    public TabSwitcher(@NonNull final Context context) {
        this(context, null);
    }

    public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet) {
        super(context, attributeSet);
        initialize(attributeSet, 0, 0);
    }

    public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet,
                       @AttrRes final int defaultStyle) {
        super(context, attributeSet, defaultStyle);
        initialize(attributeSet, defaultStyle, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TabSwitcher(@NonNull final Context context, @Nullable final AttributeSet attributeSet,
                       @AttrRes final int defaultStyle, @StyleRes final int defaultStyleResource) {
        super(context, attributeSet, defaultStyle, defaultStyleResource);
        initialize(attributeSet, defaultStyle, defaultStyleResource);
    }

    public final void addTab(@NonNull final Tab tab) {
        addTab(tab, getCount());
    }

    public final void addTab(@NonNull final Tab tab, final int index) {
        addTab(tab, index, AnimationType.SWIPE_RIGHT);
    }

    // TODO: Add support for adding tab, while switcher is shown
    public final void addTab(@NonNull final Tab tab, final int index,
                             @NonNull final AnimationType animationType) {
        ensureNotNull(tab, "The tab may not be null");
        ensureNotNull(animationType, "The animation type may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                tabs.add(index, tab);
                ViewGroup view = inflateTabView(tab);
                view.setVisibility(View.INVISIBLE);
                LayoutParams layoutParams =
                        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                int borderMargin = -(tabInset + tabBorderWidth);
                layoutParams.leftMargin = borderMargin;
                layoutParams.topMargin = -(tabInset + tabTitleContainerHeight);
                layoutParams.rightMargin = borderMargin;
                layoutParams.bottomMargin = borderMargin;
                tabContainer.addView(view, tabContainer.getChildCount() - index, layoutParams);
                TabView tabView = new TabView(index, view);

                if (tabs.size() == 1) {
                    selectedTabIndex = 0;
                    view.setVisibility(View.VISIBLE);
                    addChildView(index);
                    notifyOnSelectionChanged(0, tab);
                }

                notifyOnTabAdded(index, tab);

                if (!isSwitcherShown()) {
                    toolbar.setAlpha(0);
                } else {
                    view.getViewTreeObserver().addOnGlobalLayoutListener(
                            createAddTabViewLayoutListener(tabView, animationType));
                }
            }

        });
    }

    private void addChildView(final int index) {
        if (ViewCompat.isLaidOut(this)) {
            TabView tabView = new Iterator(false, index).next();
            Tab tab = getTab(tabView.index);
            ViewHolder viewHolder = tabView.viewHolder;
            int viewType = getDecorator().getViewType(tab);
            viewHolder.child = inflateChildView(viewHolder.childContainer, viewType);
            getDecorator().onShowTab(getContext(), this, viewHolder.child, tab, viewType);
            LayoutParams childLayoutParams =
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            childLayoutParams.setMargins(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                    getPaddingBottom());
            viewHolder.childContainer.addView(viewHolder.child, 0, childLayoutParams);
        }
    }

    private void detachChildView(final int index) {
        TabView tabView = new Iterator(false, index).next();
        ViewHolder viewHolder = tabView.viewHolder;

        if (viewHolder.childContainer.getChildCount() > 2) {
            viewHolder.childContainer.removeViewAt(0);
        }

        viewHolder.child = null;
        viewHolder.previewImageView.setImageBitmap(null);
        viewHolder.previewImageView.setVisibility(View.GONE);
    }

    // TODO: Do only render visible views
    private void renderChildViews() {
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            ViewHolder viewHolder = tabView.viewHolder;
            Tab tab = getTab(tabView.index);
            int viewType = getDecorator().getViewType(tab);
            View child = inflateChildView(viewHolder.childContainer, viewType);
            getDecorator().onShowTab(getContext(), this, child, tab, viewType);
            Bitmap bitmap = Bitmap.createBitmap(child.getWidth(), child.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            child.draw(canvas);
            viewHolder.previewImageView.setImageBitmap(bitmap);
            viewHolder.previewImageView.setVisibility(View.VISIBLE);
        }
    }

    private ViewTreeObserver.OnGlobalLayoutListener createAddTabViewLayoutListener(
            @NonNull final TabView tabView, @NonNull final AnimationType animationType) {
        return new ViewTreeObserver.OnGlobalLayoutListener() {

            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                View view = tabView.view;
                removeOnGlobalLayoutListener(view);
                view.setVisibility(View.VISIBLE);
                view.setAlpha(closedTabAlpha);
                float closedPosition = calculateClosedTabPosition();
                float dragPosition = getPosition(Axis.DRAGGING_AXIS,
                        tabContainer.getChildAt(getChildIndex(tabView.index)));
                float scale = getScale(view, true);
                setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
                setPivot(Axis.ORTHOGONAL_AXIS, view, getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
                setPosition(Axis.ORTHOGONAL_AXIS, view,
                        animationType == AnimationType.SWIPE_LEFT ? -1 * closedPosition :
                                closedPosition);
                setPosition(Axis.DRAGGING_AXIS, view, dragPosition);
                setScale(Axis.ORTHOGONAL_AXIS, view, scale);
                setScale(Axis.DRAGGING_AXIS, view, scale);
                setPivot(Axis.DRAGGING_AXIS, view, getPivotWhenClosing(Axis.DRAGGING_AXIS, view));
                setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getPivotWhenClosing(Axis.ORTHOGONAL_AXIS, view));
                setScale(Axis.ORTHOGONAL_AXIS, view, closedTabScale * scale);
                setScale(Axis.DRAGGING_AXIS, view, closedTabScale * scale);
                animateClose(tabView, false, 0, 0, createAddAnimationListener(tabView));
            }

        };
    }

    private Animator.AnimatorListener createAddAnimationListener(@NonNull final TabView tabView) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                applyTag(tabView);
                closeAnimation = null;
                executePendingAction();
            }

        };
    }

    public final void removeTab(@NonNull final Tab tab) {
        ensureNotNull(tab, "The tab may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                int index = tabs.indexOf(tab);
                int childIndex = getChildIndex(index);

                if (!isSwitcherShown()) {
                    tabContainer.removeViewAt(childIndex);
                    tabs.remove(index);
                    notifyOnTabRemoved(index, tab);

                    if (isEmpty()) {
                        selectedTabIndex = -1;
                        notifyOnSelectionChanged(-1, null);
                        toolbar.setAlpha(isToolbarShown() ? 1 : 0);
                    } else if (selectedTabIndex == index) {
                        if (selectedTabIndex > 0) {
                            selectedTabIndex--;
                        }

                        int selectedChildIndex = getChildIndex(selectedTabIndex);
                        View selectedView = tabContainer.getChildAt(selectedChildIndex);
                        addChildView(selectedTabIndex);
                        selectedView.setVisibility(View.VISIBLE);
                        notifyOnSelectionChanged(selectedTabIndex, getTab(selectedTabIndex));
                    }
                } else {
                    View view = tabContainer.getChildAt(childIndex);
                    TabView tabView = new TabView(index, view);
                    adaptTopMostTabViewWhenClosing(tabView, tabView.index + 1);
                    tabView.tag.closing = true;
                    setPivot(Axis.DRAGGING_AXIS, view,
                            getPivotWhenClosing(Axis.DRAGGING_AXIS, view));
                    setPivot(Axis.ORTHOGONAL_AXIS, view,
                            getPivotWhenClosing(Axis.ORTHOGONAL_AXIS, view));
                    animateClose(tabView, true, 0, 0, createCloseAnimationListener(tabView, true));
                }
            }

        });
    }

    public final void clear() {
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                if (!isSwitcherShown()) {
                    tabs.clear();
                    selectedTabIndex = -1;
                    removeAllViews();
                    notifyOnSelectionChanged(-1, null);
                    notifyOnAllTabsRemoved();
                    toolbar.setAlpha(isToolbarShown() ? 1 : 0);
                } else {
                    Iterator iterator = new Iterator(true);
                    TabView tabView;
                    int startDelay = 0;

                    while ((tabView = iterator.next()) != null) {
                        TabView previous = iterator.previous();

                        if (tabView.tag.state == State.VISIBLE ||
                                previous != null && previous.tag.state == State.VISIBLE) {
                            startDelay += getResources()
                                    .getInteger(android.R.integer.config_shortAnimTime);
                        }

                        animateClose(tabView, true, 0, startDelay,
                                !iterator.hasNext() ? createClearAnimationListener() : null);
                    }
                }
            }

        });
    }

    private void animateToolbarVisibility(final boolean visible, final long startDelay) {
        if (toolbarAnimation != null) {
            toolbarAnimation.cancel();
        }

        float targetAlpha = visible ? 1 : 0;

        if (toolbar.getAlpha() != targetAlpha) {
            toolbarAnimation = toolbar.animate();
            toolbarAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            toolbarAnimation.setDuration(
                    getResources().getInteger(android.R.integer.config_mediumAnimTime));
            toolbarAnimation.setStartDelay(startDelay);
            toolbarAnimation.alpha(targetAlpha);
            toolbarAnimation.start();
        }
    }

    private Animator.AnimatorListener createClearAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                tabs.clear();
                selectedTabIndex = -1;
                notifyOnAllTabsRemoved();
                notifyOnSelectionChanged(-1, null);
                animateToolbarVisibility(isToolbarShown(), 0);
                closeAnimation = null;
                executePendingAction();
            }

        };
    }

    public final void selectTab(@NonNull final Tab tab) {
        ensureNotNull(tab, "The tab may not be null");
        enqueuePendingAction(new Runnable() {

            @Override
            public void run() {
                int index = tabs.indexOf(tab);

                if (!isSwitcherShown()) {
                    int previouslySelectedChildIndex = getChildIndex(selectedTabIndex);
                    View previouslySelectedView =
                            tabContainer.getChildAt(previouslySelectedChildIndex);
                    previouslySelectedView.setVisibility(View.INVISIBLE);
                    int selectedChildIndex = getChildIndex(index);
                    View selectedView = tabContainer.getChildAt(selectedChildIndex);
                    selectedView.setVisibility(View.VISIBLE);
                    selectedTabIndex = index;
                    addChildView(index);
                    notifyOnSelectionChanged(selectedChildIndex, tab);
                } else {
                    selectedTabIndex = index;
                    hideSwitcher();
                }
            }

        });
    }

    private int getChildIndex(final int index) {
        return tabContainer.getChildCount() - (index + 1);
    }

    private void enqueuePendingAction(@NonNull final Runnable action) {
        pendingActions.add(action);
        executePendingAction();
    }

    private void executePendingAction() {
        if (!isAnimationRunning()) {
            final Runnable action = pendingActions.poll();

            if (action != null) {
                new Runnable() {

                    @Override
                    public void run() {
                        action.run();
                        executePendingAction();
                    }

                }.run();
            }
        }
    }

    @Nullable
    public final Tab getSelectedTab() {
        return selectedTabIndex != -1 ? getTab(selectedTabIndex) : null;
    }

    public final int getSelectedTabIndex() {
        return selectedTabIndex;
    }

    public final boolean isEmpty() {
        return getCount() == 0;
    }

    public final int getCount() {
        return tabs.size();
    }

    public final Tab getTab(final int index) {
        return tabs.get(index);
    }

    public final int indexOf(@NonNull final Tab tab) {
        ensureNotNull(tab, "The tab may not be null");
        return tabs.indexOf(tab);
    }

    public final boolean isSwitcherShown() {
        return switcherShown;
    }

    private int calculateTabViewMargin(@NonNull final View view) {
        Axis axis = isDraggingHorizontally() ? Axis.ORTHOGONAL_AXIS : Axis.DRAGGING_AXIS;
        return Math.round(getSize(axis, view) - (getSize(axis, tabContainer) - tabInset -
                (isDraggingHorizontally() ? 0 : STACKED_TAB_COUNT * stackedTabSpacing) -
                (isToolbarShown() ? toolbar.getHeight() - tabInset : 0)));
    }

    @SuppressWarnings("WrongConstant")
    public final void showSwitcher() {
        if (!isSwitcherShown() && !isAnimationRunning()) {
            switcherShown = true;
            notifyOnSwitcherShown();
            attachedPosition = calculateAttachedPosition();

            if (selectedTabIndex != -1) {
                detachChildView(selectedTabIndex);
            }

            renderChildViews();
            Iterator iterator = new Iterator();
            TabView tabView;

            while ((tabView = iterator.next()) != null) {
                tabView.viewHolder.borderView.setVisibility(View.VISIBLE);
                View view = tabView.view;
                setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
                setPivot(Axis.ORTHOGONAL_AXIS, view, getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
                calculateAndClipTopThresholdPosition(tabView, iterator.previous());
            }

            /*
            TabView selectedTabView = new Iterator(false, selectedTabIndex + 1).next();
            float targetPosition = getSize(Axis.DRAGGING_AXIS, tabContainer) / 2f;
            System.out.println("minTabSpacing = " + minTabSpacing);
            System.out.println("maxTabSpacing = " + maxTabSpacing);
            System.out.println("attachedPosition = " + attachedPosition);
            System.out.println(
                    "targetPosition of tab " + (selectedTabIndex + 1) + " = " + targetPosition);
            float drag = 0;

            while (getPosition(Axis.DRAGGING_AXIS, selectedTabView.view) < targetPosition) {
                drag++;

                if (!handleDrag(drag, 0)) {
                    break;
                }
            }

            handleRelease(null);
            printActualPositions();
            */

            iterator = new Iterator();

            while ((tabView = iterator.next()) != null) {
                View view = tabView.view;
                view.setVisibility(
                        tabView.index == selectedTabIndex ? View.VISIBLE : getVisibility(tabView));
                float scale = getScale(view, true);

                if (tabView.index < selectedTabIndex) {
                    setPosition(Axis.DRAGGING_AXIS, view,
                            getSize(Axis.DRAGGING_AXIS, tabContainer));
                } else if (tabView.index > selectedTabIndex) {
                    LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
                    setPosition(Axis.DRAGGING_AXIS, view,
                            isDraggingHorizontally() ? 0 : layoutParams.topMargin);
                }

                long animationDuration =
                        getResources().getInteger(android.R.integer.config_longAnimTime);
                animateMargin(view, calculateTabViewMargin(view), animationDuration);
                showSwitcherAnimation = view.animate();
                showSwitcherAnimation.setDuration(animationDuration);
                showSwitcherAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                showSwitcherAnimation.setListener(
                        createShowSwitcherAnimationListener(tabView, !iterator.hasNext()));
                animateScale(Axis.DRAGGING_AXIS, showSwitcherAnimation, scale);
                animateScale(Axis.ORTHOGONAL_AXIS, showSwitcherAnimation, scale);
                animatePosition(Axis.DRAGGING_AXIS, showSwitcherAnimation, view,
                        tabView.tag.projectedPosition, true);
                animatePosition(Axis.ORTHOGONAL_AXIS, showSwitcherAnimation, view, 0, true);
                showSwitcherAnimation.setStartDelay(0);
                showSwitcherAnimation.start();

                animateToolbarVisibility(isToolbarShown(),
                        getResources().getInteger(android.R.integer.config_shortAnimTime));
            }
        }
    }

    private void animateMargin(@NonNull final View view, final int targetMargin,
                               final long animationDuration) {
        LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
        int initialMargin = layoutParams.bottomMargin;
        marginAnimation = ValueAnimator.ofInt(targetMargin - initialMargin);
        marginAnimation.setDuration(animationDuration);
        marginAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        marginAnimation.setStartDelay(0);
        marginAnimation.addUpdateListener(createMarginAnimatorUpdateListener(view, initialMargin));
        marginAnimation.start();
    }

    private AnimatorUpdateListener createMarginAnimatorUpdateListener(@NonNull final View view,
                                                                      final int initialMargin) {
        return new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin = initialMargin + (int) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }

        };
    }

    private void printActualPositions() {
        Iterator iterator = new Iterator(true);
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            System.out.println(tabView.index + ": " + tabView.tag.actualPosition);
        }
    }

    public final void hideSwitcher() {
        if (isSwitcherShown() && !isAnimationRunning()) {
            switcherShown = false;
            notifyOnSwitcherHidden();
            Iterator iterator = new Iterator();
            TabView tabView;

            while ((tabView = iterator.next()) != null) {
                View view = tabView.view;
                long animationDuration =
                        getResources().getInteger(android.R.integer.config_longAnimTime);
                animateMargin(view, -(tabInset + tabBorderWidth), animationDuration);
                hideSwitcherAnimation = view.animate();
                hideSwitcherAnimation.setDuration(animationDuration);
                hideSwitcherAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                hideSwitcherAnimation.setListener(
                        createHideSwitcherAnimationListener(tabView, !iterator.hasNext()));
                animateScale(Axis.DRAGGING_AXIS, hideSwitcherAnimation, 1);
                animateScale(Axis.ORTHOGONAL_AXIS, hideSwitcherAnimation, 1);
                LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
                animatePosition(Axis.ORTHOGONAL_AXIS, hideSwitcherAnimation, view,
                        isDraggingHorizontally() ? layoutParams.topMargin : 0, false);

                if (tabView.index < selectedTabIndex) {
                    animatePosition(Axis.DRAGGING_AXIS, hideSwitcherAnimation, view,
                            getSize(Axis.DRAGGING_AXIS, this), false);
                } else if (tabView.index > selectedTabIndex) {
                    animatePosition(Axis.DRAGGING_AXIS, hideSwitcherAnimation, view,
                            isDraggingHorizontally() ? 0 : layoutParams.topMargin, false);
                } else {
                    view.setVisibility(View.VISIBLE);
                    animatePosition(Axis.DRAGGING_AXIS, hideSwitcherAnimation, view,
                            isDraggingHorizontally() ? 0 : layoutParams.topMargin, false);
                }

                hideSwitcherAnimation.setStartDelay(0);
                hideSwitcherAnimation.start();
                animateToolbarVisibility(isToolbarShown() && isEmpty(), 0);
            }
        }
    }

    public final void toggleSwitcherVisibility() {
        if (switcherShown) {
            hideSwitcher();
        } else {
            showSwitcher();
        }
    }

    private Animator.AnimatorListener createShowSwitcherAnimationListener(
            @NonNull final TabView tabView, final boolean reset) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                applyTag(tabView);

                if (reset) {
                    showSwitcherAnimation = null;
                    marginAnimation = null;
                    executePendingAction();
                }
            }

        };
    }

    private Animator.AnimatorListener createHideSwitcherAnimationListener(
            @NonNull final TabView tabView, final boolean reset) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                View view = tabView.view;
                tabView.viewHolder.borderView.setVisibility(View.GONE);
                detachChildView(tabView.index);

                if (tabView.index == selectedTabIndex) {
                    addChildView(tabView.index);
                } else {
                    view.setVisibility(View.INVISIBLE);
                }

                if (reset) {
                    hideSwitcherAnimation = null;
                    marginAnimation = null;
                    executePendingAction();
                }
            }

        };
    }

    private float calculateAttachedPosition() {
        return ((maxTabSpacing - minTabSpacing) / (1 - NON_LINEAR_DRAG_FACTOR)) *
                NON_LINEAR_DRAG_FACTOR + calculateFirstTabTopThresholdPosition();
    }

    private Animation.AnimationListener createDragAnimationListener() {
        return new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(final Animation animation) {

            }

            @Override
            public void onAnimationEnd(final Animation animation) {
                handleRelease(null);
                dragAnimation = null;
                executePendingAction();
            }

            @Override
            public void onAnimationRepeat(final Animation animation) {

            }

        };
    }

    private Animator.AnimatorListener createOvershootAnimationListenerWrapper(
            @NonNull final View view, @Nullable final Animator.AnimatorListener listener) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
                setPivot(Axis.ORTHOGONAL_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));

                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
            }

        };
    }

    private Animator.AnimatorListener createOvershootDownAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                handleRelease(null);
                overshootAnimation = null;
                executePendingAction();
            }

        };
    }

    private Animation.AnimationListener createOvershootUpAnimationListener() {
        return new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                handleRelease(null);
                overshootUpAnimation = null;
                executePendingAction();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

        };
    }

    private void dragToTopThresholdPosition() {
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            calculateAndClipTopThresholdPosition(tabView, iterator.previous());
            applyTag(tabView);
        }
    }

    private void calculateAndClipTopThresholdPosition(@NonNull final TabView tabView,
                                                      @Nullable final TabView previous) {
        float position = calculateTopThresholdPosition(tabView, previous);
        clipDraggedTabPosition(position, tabView, previous);
    }

    private float calculateTopThresholdPosition(@NonNull final TabView tabView,
                                                @Nullable final TabView previous) {
        if (previous == null) {
            return calculateFirstTabTopThresholdPosition();
        } else {
            if (tabView.index == 1) {
                return previous.tag.actualPosition - minTabSpacing;
            } else {
                return previous.tag.actualPosition - maxTabSpacing;
            }
        }
    }

    private float calculateFirstTabTopThresholdPosition() {
        return tabContainer.getChildCount() > STACKED_TAB_COUNT ?
                STACKED_TAB_COUNT * stackedTabSpacing :
                (tabContainer.getChildCount() - 1) * stackedTabSpacing;
    }

    private void dragToBottomThresholdPosition() {
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            calculateAndClipBottomThresholdPosition(tabView, iterator.previous());
            applyTag(tabView);
        }
    }

    private void calculateAndClipBottomThresholdPosition(@NonNull final TabView tabView,
                                                         @Nullable final TabView previous) {
        float position = calculateBottomThresholdPosition(tabView);
        clipDraggedTabPosition(position, tabView, previous);
    }

    private float calculateBottomThresholdPosition(@NonNull final TabView tabView) {
        return (tabContainer.getChildCount() - (tabView.index + 1)) * maxTabSpacing;
    }

    private void updateTags() {
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            View view = tabView.view;
            Tag tag = tabView.tag;
            tag.projectedPosition = getPosition(Axis.DRAGGING_AXIS, view);
            tag.distance = 0;
        }
    }

    private void applyTag(@NonNull final TabView tabView) {
        Tag tag = tabView.tag;
        float position = tag.projectedPosition;
        View view = tabView.view;
        setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
        setPosition(Axis.DRAGGING_AXIS, view, position);
        setRotation(Axis.ORTHOGONAL_AXIS, view, 0);
        adaptVisibility(tabView);
    }

    @SuppressWarnings("WrongConstant")
    private void adaptVisibility(@NonNull final TabView tabView) {
        View view = tabView.view;
        view.setVisibility(getVisibility(tabView));
    }

    private int getVisibility(@NonNull final TabView tabView) {
        State state = tabView.tag.state;
        return (state == State.TOP_MOST_HIDDEN || state == State.BOTTOM_MOST_HIDDEN) &&
                !tabView.tag.closing ? View.INVISIBLE : View.VISIBLE;
    }

    private void calculateNonLinearPositionWhenDraggingDown(final float dragDistance,
                                                            @NonNull final TabView tabView,
                                                            @Nullable final TabView previous,
                                                            final float currentPosition) {
        if (previous != null && previous.tag.state == State.VISIBLE &&
                tabView.tag.state == State.VISIBLE) {
            float newPosition = calculateNonLinearPosition(dragDistance, currentPosition, tabView);

            if (previous.tag.projectedPosition - newPosition >= maxTabSpacing) {
                lastAttachedIndex = tabView.index;
                newPosition = previous.tag.projectedPosition - maxTabSpacing;
            }

            clipDraggedTabPosition(newPosition, tabView, previous);
        }
    }

    private void calculateTabPosition(final float dragDistance, @NonNull final TabView tabView,
                                      @Nullable final TabView previous) {
        if (tabContainer.getChildCount() - tabView.index > 1) {
            float distance = dragDistance - tabView.tag.distance;
            tabView.tag.distance = dragDistance;

            if (distance != 0) {
                float currentPosition = tabView.tag.actualPosition;
                float newPosition = currentPosition + distance;
                clipDraggedTabPosition(newPosition, tabView, previous);

                if (scrollDirection == ScrollDirection.DRAGGING_DOWN) {
                    calculateNonLinearPositionWhenDraggingDown(distance, tabView, previous,
                            currentPosition);
                } else if (scrollDirection == ScrollDirection.DRAGGING_UP) {
                    calculateNonLinearPositionWhenDraggingUp(distance, tabView, previous,
                            currentPosition);
                }
            }
        }
    }

    private void calculateNonLinearPositionWhenDraggingUp(final float dragDistance,
                                                          @NonNull final TabView tabView,
                                                          @Nullable final TabView previous,
                                                          final float currentPosition) {
        if (tabView.tag.state == State.VISIBLE) {
            boolean attached = tabView.tag.projectedPosition > attachedPosition;

            if (previous == null || attached) {
                lastAttachedIndex = tabView.index;
            }

            if (previous != null && !attached) {
                float newPosition =
                        calculateNonLinearPosition(dragDistance, currentPosition, tabView);

                if (previous.tag.state != State.STACKED_BOTTOM &&
                        previous.tag.state != State.BOTTOM_MOST_HIDDEN &&
                        previous.tag.projectedPosition - newPosition <= minTabSpacing) {
                    newPosition = previous.tag.projectedPosition - minTabSpacing;
                }

                clipDraggedTabPosition(newPosition, tabView, previous);
            }
        }
    }

    private float calculateNonLinearPosition(final float dragDistance, final float currentPosition,
                                             @NonNull final TabView tabView) {
        return currentPosition + (float) (dragDistance *
                Math.pow(NON_LINEAR_DRAG_FACTOR, tabView.index - lastAttachedIndex));
    }

    private void clipDraggedTabPosition(final float dragPosition, @NonNull final TabView tabView,
                                        @Nullable final TabView previous) {
        Pair<Float, State> topMostPair = calculateTopMostPositionAndState(tabView, previous);
        float topMostPosition = topMostPair.first;

        if (dragPosition <= topMostPosition) {
            tabView.tag.projectedPosition = topMostPair.first;
            tabView.tag.actualPosition = dragPosition;
            tabView.tag.state = topMostPair.second;
            return;
        } else {
            Pair<Float, State> bottomMostPair = calculateBottomMostPositionAndState(tabView);
            float bottomMostPosition = bottomMostPair.first;

            if (dragPosition >= bottomMostPosition) {
                tabView.tag.projectedPosition = bottomMostPair.first;
                tabView.tag.actualPosition = dragPosition;
                tabView.tag.state = bottomMostPair.second;
                return;
            }
        }

        tabView.tag.projectedPosition = dragPosition;
        tabView.tag.actualPosition = dragPosition;
        tabView.tag.state = State.VISIBLE;
    }

    private Pair<Float, State> calculateTopMostPositionAndState(@NonNull final TabView tabView,
                                                                @Nullable final TabView previous) {
        if ((tabContainer.getChildCount() - tabView.index) <= STACKED_TAB_COUNT) {
            float position =
                    stackedTabSpacing * (tabContainer.getChildCount() - (tabView.index + 1));
            return Pair.create(position,
                    (previous == null || previous.tag.state == State.VISIBLE) ? State.TOP_MOST :
                            State.STACKED_TOP);
        } else {
            float position = stackedTabSpacing * STACKED_TAB_COUNT;
            return Pair.create(position,
                    (previous == null || previous.tag.state == State.VISIBLE) ? State.TOP_MOST :
                            State.TOP_MOST_HIDDEN);
        }
    }

    private Pair<Float, State> calculateBottomMostPositionAndState(@NonNull final TabView tabView) {
        float size = getSize(Axis.DRAGGING_AXIS, tabContainer);
        int toolbarHeight =
                isToolbarShown() && !isDraggingHorizontally() ? toolbar.getHeight() - tabInset : 0;
        int padding = getPadding(Axis.DRAGGING_AXIS, Gravity.START) +
                getPadding(Axis.DRAGGING_AXIS, Gravity.END);

        if (tabView.index < STACKED_TAB_COUNT) {
            float position =
                    size - toolbarHeight - tabInset - (stackedTabSpacing * (tabView.index + 1)) -
                            padding;
            return Pair.create(position, State.STACKED_BOTTOM);
        } else {
            float position =
                    size - toolbarHeight - tabInset - (stackedTabSpacing * STACKED_TAB_COUNT) -
                            padding;
            return Pair.create(position, State.BOTTOM_MOST_HIDDEN);
        }
    }

    @Override
    public final boolean onTouchEvent(final MotionEvent event) {
        if (isSwitcherShown()) {
            if (dragAnimation != null) {
                dragAnimation.cancel();
                dragAnimation = null;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    handleDown(event);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!isAnimationRunning() && event.getPointerId(0) == pointerId) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }

                        velocityTracker.addMovement(event);
                        handleDrag(getPosition(Axis.DRAGGING_AXIS, event),
                                getPosition(Axis.ORTHOGONAL_AXIS, event));
                    } else {
                        handleRelease(null);
                        handleDown(event);
                    }

                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isAnimationRunning() && event.getPointerId(0) == pointerId) {
                        handleRelease(event);
                    }

                    return true;
                default:
                    break;
            }
        }

        return super.onTouchEvent(event);
    }

    private boolean isAnimationRunning() {
        return showSwitcherAnimation != null || marginAnimation != null ||
                hideSwitcherAnimation != null || overshootAnimation != null ||
                overshootUpAnimation != null || closeAnimation != null || relocateAnimation != null;
    }

    private void handleDown(@NonNull final MotionEvent event) {
        pointerId = event.getPointerId(0);

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }

        velocityTracker.addMovement(event);
    }

    private boolean isTopDragThresholdReached() {
        if (tabContainer.getChildCount() <= 1) {
            return true;
        } else {
            View view = tabContainer.getChildAt(tabContainer.getChildCount() - 1);
            Tag tag = (Tag) view.getTag(R.id.tag_properties);
            return tag.state == State.TOP_MOST;
        }
    }

    private boolean isBottomDragThresholdReached() {
        if (tabContainer.getChildCount() <= 1) {
            return true;
        } else {
            View view = tabContainer.getChildAt(1);
            Tag tag = (Tag) view.getTag(R.id.tag_properties);
            return tag.projectedPosition >= maxTabSpacing;
        }
    }

    private void tiltOnOvershootDown(final float angle) {
        float maxCameraDistance = getMaxCameraDistance();
        float minCameraDistance = maxCameraDistance / 2f;
        int firstVisibleIndex = -1;
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            View view = tabView.view;

            if (!iterator.hasNext()) {
                view.setCameraDistance(maxCameraDistance);
            } else if (firstVisibleIndex == -1) {
                view.setCameraDistance(minCameraDistance);

                if (tabView.tag.state == State.VISIBLE) {
                    firstVisibleIndex = tabView.index;
                }
            } else {
                int diff = tabView.index - firstVisibleIndex;
                float ratio =
                        (float) diff / (float) (tabContainer.getChildCount() - firstVisibleIndex);
                view.setCameraDistance(
                        minCameraDistance + (maxCameraDistance - minCameraDistance) * ratio);
            }

            setPivot(Axis.DRAGGING_AXIS, view, getPivotOnOvershootDown(Axis.DRAGGING_AXIS, view));
            setPivot(Axis.ORTHOGONAL_AXIS, view,
                    getPivotOnOvershootDown(Axis.ORTHOGONAL_AXIS, view));
            setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
        }
    }

    private void tiltOnOvershootUp(final float angle) {
        float cameraDistance = getMaxCameraDistance();
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            View view = tabView.view;

            if (tabView.index == 0) {
                view.setVisibility(View.VISIBLE);
                view.setCameraDistance(cameraDistance);
                setPivot(Axis.DRAGGING_AXIS, view, getPivotOnOvershootUp(Axis.DRAGGING_AXIS, view));
                setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getPivotOnOvershootUp(Axis.ORTHOGONAL_AXIS, view));
                setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
            } else {
                view.setVisibility(View.INVISIBLE);
            }
        }
    }

    private float getMaxCameraDistance() {
        float density = getResources().getDisplayMetrics().density;
        return density * 1280;
    }

    @SuppressWarnings("WrongConstant")
    private boolean handleDrag(final float dragPosition, final float orthogonalPosition) {
        if (dragPosition <= topDragThreshold) {
            if (!dragHelper.isReset()) {
                dragHelper.reset(0);
                updateTags();
            }

            scrollDirection = ScrollDirection.OVERSHOOT_UP;
            overshootDragHelper.update(dragPosition);
            float overshootDistance = Math.abs(overshootDragHelper.getDragDistance());

            if (overshootDistance <= maxOvershootDistance) {
                float ratio = Math.max(0, Math.min(1, overshootDistance / maxOvershootDistance));
                Iterator iterator = new Iterator();
                TabView tabView;

                while ((tabView = iterator.next()) != null) {
                    View view = tabView.view;

                    if (tabView.index == 0) {
                        float currentPosition = tabView.tag.projectedPosition;
                        setPivot(Axis.DRAGGING_AXIS, view,
                                getDefaultPivot(Axis.DRAGGING_AXIS, view));
                        setPivot(Axis.ORTHOGONAL_AXIS, view,
                                getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
                        setPosition(Axis.DRAGGING_AXIS, view,
                                currentPosition - (currentPosition * ratio));
                    } else {
                        View firstView = iterator.first().view;
                        view.setVisibility(getPosition(Axis.DRAGGING_AXIS, firstView) <=
                                getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
                                getVisibility(tabView));
                    }
                }
            } else {
                float ratio = Math.max(0, Math.min(1,
                        (overshootDistance - maxOvershootDistance) / maxOvershootDistance));
                tiltOnOvershootUp(ratio * MAX_UP_OVERSHOOT_ANGLE);
            }
        } else if (dragPosition >= bottomDragThreshold) {
            if (!dragHelper.isReset()) {
                dragHelper.reset(0);
                updateTags();
            }

            scrollDirection = ScrollDirection.OVERSHOOT_DOWN;
            overshootDragHelper.update(dragPosition);
            float overshootDistance = overshootDragHelper.getDragDistance();
            float ratio = Math.max(0, Math.min(1, overshootDistance / maxOvershootDistance));
            tiltOnOvershootDown(ratio * -MAX_DOWN_OVERSHOOT_ANGLE);
        } else {
            overshootDragHelper.reset();
            float previousDistance = dragHelper.isReset() ? 0 : dragHelper.getDragDistance();
            dragHelper.update(dragPosition);
            closeDragHelper.update(orthogonalPosition);

            if (scrollDirection == ScrollDirection.NONE && draggedTabView == null &&
                    closeDragHelper.hasThresholdBeenReached()) {
                TabView tabView = getFocusedTabView(dragHelper.getDragStartPosition());

                if (tabView != null && getTab(tabView.index).isCloseable()) {
                    draggedTabView = tabView;
                }
            }

            if (draggedTabView == null && dragHelper.hasThresholdBeenReached()) {
                if (scrollDirection == ScrollDirection.OVERSHOOT_UP) {
                    scrollDirection = ScrollDirection.DRAGGING_DOWN;
                } else if (scrollDirection == ScrollDirection.OVERSHOOT_DOWN) {
                    scrollDirection = ScrollDirection.DRAGGING_UP;
                } else {
                    scrollDirection = previousDistance - dragHelper.getDragDistance() <= 0 ?
                            ScrollDirection.DRAGGING_DOWN : ScrollDirection.DRAGGING_UP;
                }
            }

            if (draggedTabView != null) {
                handleDragToClose();
            } else if (scrollDirection != ScrollDirection.NONE) {
                lastAttachedIndex = 0;
                Iterator iterator = new Iterator();
                TabView tabView;

                while ((tabView = iterator.next()) != null) {
                    calculateTabPosition(dragHelper.getDragDistance(), tabView,
                            iterator.previous());
                    applyTag(tabView);
                }

                checkIfDragThresholdReached(dragPosition);
            }

            return true;
        }

        return false;
    }

    private boolean checkIfDragThresholdReached(final float dragPosition) {
        if (isBottomDragThresholdReached() && (scrollDirection == ScrollDirection.DRAGGING_DOWN ||
                scrollDirection == ScrollDirection.OVERSHOOT_DOWN)) {
            bottomDragThreshold = dragPosition;
            scrollDirection = ScrollDirection.OVERSHOOT_DOWN;
            dragToBottomThresholdPosition();
            return true;
        } else if (isTopDragThresholdReached() && (scrollDirection == ScrollDirection.DRAGGING_UP ||
                scrollDirection == ScrollDirection.OVERSHOOT_UP)) {
            topDragThreshold = dragPosition;
            scrollDirection = ScrollDirection.OVERSHOOT_UP;
            dragToTopThresholdPosition();
            return true;
        }

        return false;
    }

    private void handleDragToClose() {
        View view = draggedTabView.view;

        if (!draggedTabView.tag.closing) {
            adaptTopMostTabViewWhenClosing(draggedTabView, draggedTabView.index + 1);
        }

        draggedTabView.tag.closing = true;
        float dragDistance = closeDragHelper.getDragDistance();
        setPivot(Axis.DRAGGING_AXIS, view, getPivotWhenClosing(Axis.DRAGGING_AXIS, view));
        setPivot(Axis.ORTHOGONAL_AXIS, view, getPivotWhenClosing(Axis.ORTHOGONAL_AXIS, view));
        float scale = getScale(view, true);
        setPosition(Axis.ORTHOGONAL_AXIS, view, dragDistance);
        float ratio = 1 - (Math.abs(dragDistance) / calculateClosedTabPosition());
        float scaledClosedTabScale = closedTabScale * scale;
        float targetScale = scaledClosedTabScale + ratio * (scale - scaledClosedTabScale);
        setScale(Axis.DRAGGING_AXIS, view, targetScale);
        setScale(Axis.ORTHOGONAL_AXIS, view, targetScale);
        view.setAlpha(closedTabAlpha + ratio * (1 - closedTabAlpha));
    }

    private void adaptTopMostTabViewWhenClosing(@NonNull final TabView closedTabView,
                                                final int index) {
        if (closedTabView.tag.state == State.TOP_MOST) {
            Iterator iterator = new Iterator(false, index);
            TabView tabView = iterator.next();

            if (tabView != null) {
                if (tabView.tag.state == State.TOP_MOST_HIDDEN) {
                    tabView.tag.state = State.TOP_MOST;
                }

                adaptVisibility(tabView);
            }
        }
    }

    private void adaptTopMostTabViewWhenClosingAborted(@NonNull final TabView closedTabView,
                                                       final int index) {
        if (closedTabView.tag.state == State.TOP_MOST) {
            Iterator iterator = new Iterator(false, index);
            TabView tabView = iterator.next();

            if (tabView != null) {
                if (tabView.tag.state == State.TOP_MOST) {
                    tabView.tag.state = State.TOP_MOST_HIDDEN;
                    adaptVisibility(tabView);
                }
            }
        }
    }

    private float calculateClosedTabPosition() {
        return getSize(Axis.ORTHOGONAL_AXIS, tabContainer);
    }

    @Nullable
    private TabView getFocusedTabView(final float position) {
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            if (tabView.tag.state == State.VISIBLE || tabView.tag.state == State.TOP_MOST) {
                View view = tabView.view;
                float toolbarHeight = isToolbarShown() && !isDraggingHorizontally() ?
                        toolbar.getHeight() - tabInset : 0;
                float viewPosition = getPosition(Axis.DRAGGING_AXIS, view) + toolbarHeight +
                        getPadding(Axis.DRAGGING_AXIS, Gravity.START);

                if (viewPosition <= position) {
                    return tabView;
                }
            }
        }

        return null;
    }

    private void handleRelease(@Nullable final MotionEvent event) {
        boolean thresholdReached = dragHelper.hasThresholdBeenReached();
        ScrollDirection flingDirection = this.scrollDirection;
        this.dragHelper.reset(dragThreshold);
        this.overshootDragHelper.reset();
        this.closeDragHelper.reset();
        this.topDragThreshold = -Float.MAX_VALUE;
        this.bottomDragThreshold = Float.MAX_VALUE;
        this.scrollDirection = ScrollDirection.NONE;

        if (draggedTabView != null) {
            float flingVelocity = 0;

            if (event != null && velocityTracker != null) {
                int pointerId = event.getPointerId(0);
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                flingVelocity = Math.abs(velocityTracker.getXVelocity(pointerId));
            }

            View view = draggedTabView.view;
            boolean close = flingVelocity >= minCloseFlingVelocity ||
                    Math.abs(getPosition(Axis.ORTHOGONAL_AXIS, view)) >
                            getSize(Axis.ORTHOGONAL_AXIS, view) / 4f;
            animateClose(draggedTabView, close, flingVelocity, 0,
                    createCloseAnimationListener(draggedTabView, close));
        } else if (flingDirection == ScrollDirection.DRAGGING_UP ||
                flingDirection == ScrollDirection.DRAGGING_DOWN) {
            updateTags();

            if (event != null && velocityTracker != null && thresholdReached) {
                animateFling(event, flingDirection);
            }
        } else if (flingDirection == ScrollDirection.OVERSHOOT_DOWN) {
            updateTags();
            animateOvershootDown();
        } else if (flingDirection == ScrollDirection.OVERSHOOT_UP) {
            animateOvershootUp();
        } else if (event != null && !dragHelper.hasThresholdBeenReached() &&
                !closeDragHelper.hasThresholdBeenReached()) {
            handleClick(event);
        } else {
            updateTags();
        }

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void handleClick(@NonNull final MotionEvent event) {
        TabView tabView = getFocusedTabView(getPosition(Axis.DRAGGING_AXIS, event));

        if (tabView != null) {
            Tab tab = getTab(tabView.index);
            selectTab(tab);
        }
    }

    private void animateOvershootDown() {
        animateTilt(new AccelerateDecelerateInterpolator(), createOvershootDownAnimationListener(),
                MAX_DOWN_OVERSHOOT_ANGLE);
    }

    private void animateOvershootUp() {
        boolean tilted = animateTilt(new AccelerateInterpolator(), createTiltAnimationListener(),
                MAX_UP_OVERSHOOT_ANGLE);

        if (!tilted) {
            animateOvershootUp(new AccelerateDecelerateInterpolator());
        }
    }

    private Animator.AnimatorListener createTiltAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                animateOvershootUp(new DecelerateInterpolator());
                overshootAnimation = null;
                executePendingAction();
            }

        };
    }

    private void animateOvershootUp(@NonNull final Interpolator interpolator) {
        TabView tabView = new Iterator().next();
        View view = tabView.view;
        setPivot(Axis.DRAGGING_AXIS, view, getDefaultPivot(Axis.DRAGGING_AXIS, view));
        setPivot(Axis.ORTHOGONAL_AXIS, view, getDefaultPivot(Axis.ORTHOGONAL_AXIS, view));
        float position = getPosition(Axis.DRAGGING_AXIS, view);
        float targetPosition = tabView.tag.projectedPosition;
        long animationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        overshootUpAnimation = new OvershootUpAnimation();
        overshootUpAnimation.setFillAfter(true);
        overshootUpAnimation.setDuration(Math.round(animationDuration * Math.abs(
                (targetPosition - position) / (float) (STACKED_TAB_COUNT * stackedTabSpacing))));
        overshootUpAnimation.setInterpolator(interpolator);
        overshootUpAnimation.setAnimationListener(createOvershootUpAnimationListener());
        startAnimation(overshootUpAnimation);
    }

    private boolean animateTilt(@NonNull final Interpolator interpolator,
                                @Nullable final Animator.AnimatorListener listener,
                                final float maxAngle) {
        long animationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        Iterator iterator = new Iterator(true);
        TabView tabView;
        boolean result = false;

        while ((tabView = iterator.next()) != null) {
            View view = tabView.view;

            if (getRotation(Axis.ORTHOGONAL_AXIS, view) != 0) {
                result = true;
                overshootAnimation = view.animate();
                overshootAnimation.setListener(createOvershootAnimationListenerWrapper(view,
                        iterator.hasNext() ? null : listener));
                overshootAnimation.setDuration(Math.round(animationDuration *
                        (Math.abs(getRotation(Axis.ORTHOGONAL_AXIS, view)) / maxAngle)));
                overshootAnimation.setInterpolator(interpolator);
                animateRotation(Axis.ORTHOGONAL_AXIS, overshootAnimation, 0);
                overshootAnimation.setStartDelay(0);
                overshootAnimation.start();
            }
        }

        return result;
    }

    private void animateFling(@NonNull final MotionEvent event,
                              @NonNull final ScrollDirection flingDirection) {
        int pointerId = event.getPointerId(0);
        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
        float flingVelocity = Math.abs(velocityTracker.getYVelocity(pointerId));

        if (flingVelocity > minFlingVelocity) {
            float flingDistance = 0.25f * flingVelocity;

            if (flingDirection == ScrollDirection.DRAGGING_UP) {
                flingDistance = -1 * flingDistance;
            }

            dragAnimation = new FlingAnimation(flingDistance);
            dragAnimation.setFillAfter(true);
            dragAnimation.setAnimationListener(createDragAnimationListener());
            dragAnimation.setDuration(Math.round(Math.abs(flingDistance) / flingVelocity * 1000));
            dragAnimation.setInterpolator(new DecelerateInterpolator());
            startAnimation(dragAnimation);
        }
    }

    public final void setDecorator(@NonNull final Decorator decorator) {
        ensureNotNull(decorator, "The decorator may not be null");
        this.decorator = decorator;
        this.childViews = null;
    }

    public final Decorator getDecorator() {
        ensureNotNull(decorator, "No decorator has been set", IllegalStateException.class);
        return decorator;
    }

    public final void addListener(@NonNull final Listener listener) {
        ensureNotNull(listener, "The listener may not be null");
        this.listeners.add(listener);
    }

    public final void removeListener(@NonNull final Listener listener) {
        ensureNotNull(listener, "The listener may not be null");
        this.listeners.remove(listener);
    }

    @NonNull
    public final Toolbar getToolbar() {
        return toolbar;
    }

    public final void showToolbar(final boolean show) {
        toolbar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    public final boolean isToolbarShown() {
        return toolbar.getVisibility() == View.VISIBLE;
    }

    public final void setToolbarTitle(@Nullable final CharSequence title) {
        toolbar.setTitle(title);
    }

    public final void setToolbarTitle(@StringRes final int resourceId) {
        setToolbarTitle(getContext().getText(resourceId));
    }

    public final void inflateToolbarMenu(@MenuRes final int resourceId,
                                         @Nullable final OnMenuItemClickListener listener) {
        toolbar.inflateMenu(resourceId);
        toolbar.setOnMenuItemClickListener(listener);

    }

    public final Menu getToolbarMenu() {
        return toolbar.getMenu();
    }

    public static void setupWithMenu(@NonNull final TabSwitcher tabSwitcher,
                                     @NonNull final Menu menu,
                                     @Nullable final OnClickListener listener) {
        ensureNotNull(tabSwitcher, "The tab switcher may not be null");
        ensureNotNull(menu, "The menu may not be null");

        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);
            View view = menuItem.getActionView();

            if (view instanceof TabSwitcherButton) {
                TabSwitcherButton tabSwitcherButton = (TabSwitcherButton) view;
                tabSwitcherButton.setOnClickListener(listener);
                tabSwitcherButton.setCount(tabSwitcher.getCount());
                tabSwitcher.addListener(tabSwitcherButton);
            }
        }
    }

    public final void setToolbarNavigationIcon(@Nullable final Drawable icon,
                                               @Nullable final OnClickListener listener) {
        toolbar.setNavigationIcon(icon);
        toolbar.setNavigationOnClickListener(listener);
    }

    public final void setToolbarNavigationIcon(@DrawableRes final int resourceId,
                                               @Nullable final OnClickListener listener) {
        setToolbarNavigationIcon(ContextCompat.getDrawable(getContext(), resourceId), listener);
    }

    @Override
    public final void setPadding(final int left, final int top, final int right, final int bottom) {
        padding = new int[]{left, top, right, bottom};
        LayoutParams toolbarLayoutParams = (LayoutParams) toolbar.getLayoutParams();
        toolbarLayoutParams.setMargins(left, top, right, 0);
        Iterator iterator = new Iterator();
        TabView tabView;

        while ((tabView = iterator.next()) != null) {
            ViewHolder viewHolder = tabView.viewHolder;
            adaptChildAndPreviewMargins(viewHolder);
        }
    }

    private void adaptChildAndPreviewMargins(@NonNull final ViewHolder viewHolder) {
        if (viewHolder.child != null) {
            LayoutParams childLayoutParams = (LayoutParams) viewHolder.child.getLayoutParams();
            childLayoutParams.setMargins(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                    getPaddingBottom());
        }

        LayoutParams previewLayoutParams =
                (LayoutParams) viewHolder.previewImageView.getLayoutParams();
        previewLayoutParams.setMargins(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                getPaddingBottom());
    }

    @Override
    public final int getPaddingLeft() {
        return padding[0];
    }

    @Override
    public final int getPaddingTop() {
        return padding[1];
    }

    @Override
    public final int getPaddingRight() {
        return padding[2];
    }

    @Override
    public final int getPaddingBottom() {
        return padding[3];
    }

    @Override
    public final int getPaddingStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getPaddingRight() :
                    getPaddingLeft();
        }

        return getPaddingLeft();
    }

    @Override
    public final int getPaddingEnd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getPaddingLeft() :
                    getPaddingRight();
        }

        return getPaddingRight();
    }

    @Override
    public final void onGlobalLayout() {
        removeOnGlobalLayoutListener(this);

        if (selectedTabIndex != -1) {
            addChildView(selectedTabIndex);
        }
    }

    // TODO: Add method including API check to ViewUtil
    private void removeOnGlobalLayoutListener(@NonNull final View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
            view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    }

}