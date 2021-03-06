/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tbruyelle.rxpermissions2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;

public class RxPermissions {

	static final String TAG = RxPermissions.class.getSimpleName();
	static final Object TRIGGER = new Object();

	@VisibleForTesting
	Lazy<RxPermissionsFragment> mRxPermissionsFragment;

	public RxPermissions(@NonNull final FragmentActivity activity) {
		mRxPermissionsFragment = getLazySingleton(activity.getSupportFragmentManager());
	}

	public RxPermissions(@NonNull final Fragment fragment) {
		mRxPermissionsFragment = getLazySingleton(fragment.getChildFragmentManager());
	}

	@NonNull
	private Lazy<RxPermissionsFragment> getLazySingleton(@NonNull final FragmentManager fragmentManager) {
		return new Lazy<RxPermissionsFragment>() {

			private RxPermissionsFragment rxPermissionsFragment;

			@Override
			public synchronized RxPermissionsFragment get() {
				if (rxPermissionsFragment == null) {
					rxPermissionsFragment = getRxPermissionsFragment(fragmentManager);
				}
				return rxPermissionsFragment;
			}

		};
	}

	private RxPermissionsFragment getRxPermissionsFragment(@NonNull final FragmentManager fragmentManager) {
		RxPermissionsFragment rxPermissionsFragment = findRxPermissionsFragment(fragmentManager);
		boolean isNewInstance = rxPermissionsFragment == null;
		if (isNewInstance) {
			rxPermissionsFragment = new RxPermissionsFragment();
			fragmentManager
				.beginTransaction()
				.add(rxPermissionsFragment, TAG)
				.commitNow();
		}
		return rxPermissionsFragment;
	}

	private RxPermissionsFragment findRxPermissionsFragment(@NonNull final FragmentManager fragmentManager) {
		return (RxPermissionsFragment) fragmentManager.findFragmentByTag(TAG);
	}

	public void setLogging(boolean logging) {
		mRxPermissionsFragment.get().setLogging(logging);
	}

	/**
	 * Map emitted items from the source observable into {@code true} if permissions in parameters
	 * are granted, or {@code false} if not.
	 * <p>
	 * If one or several permissions have never been requested, invoke the related framework method
	 * to ask the user if he allows the permissions.
	 */
	@SuppressWarnings("WeakerAccess")
	public <T> ObservableTransformer<T, Boolean> ensure(final String... permissions) {
		return new ObservableTransformer<T, Boolean>() {
			@Override
			public ObservableSource<Boolean> apply(Observable<T> o) {
				return request(o, permissions)
					// Transform Observable<Permission> to Observable<Boolean>
					.buffer(permissions.length)
					.flatMap(new Function<List<Permission>, ObservableSource<Boolean>>() {
						@Override
						public ObservableSource<Boolean> apply(List<Permission> permissions) {
							if (permissions.isEmpty()) {
								// Occurs during orientation change, when the subject receives onComplete.
								// In that case we don't want to propagate that empty list to the
								// subscriber, only the onComplete.
								return Observable.empty();
							}
							// Return true if all permissions are granted.
							for (Permission p : permissions) {
								if (!p.granted) {
									return Observable.just(false);
								}
							}
							return Observable.just(true);
						}
					});
			}
		};
	}

	public <T> ObservableTransformer<T, Boolean> ensure(final SystemPermission systemPermission) {
		return new ObservableTransformer<T, Boolean>() {
			@Override
			public ObservableSource<Boolean> apply(Observable<T> o) {
				return requestImplementation(systemPermission)
					// Transform Observable<Permission> to Observable<Boolean>
					.flatMap(new Function<Permission, ObservableSource<Boolean>>() {
						@Override
						public ObservableSource<Boolean> apply(Permission permission) {
							// Return true if all permissions are granted.
							return Observable.just(permission.granted);
						}
					});
			}
		};
	}

	/**
	 * Map emitted items from the source observable into {@link Permission} objects for each
	 * permission in parameters.
	 * <p>
	 * If one or several permissions have never been requested, invoke the related framework method
	 * to ask the user if he allows the permissions.
	 */
	@SuppressWarnings("WeakerAccess")
	public <T> ObservableTransformer<T, Permission> ensureEach(final String... permissions) {
		return new ObservableTransformer<T, Permission>() {
			@Override
			public ObservableSource<Permission> apply(Observable<T> o) {
				return request(o, permissions);
			}
		};
	}

	/**
	 * Map emitted items from the source observable into one combined {@link Permission} object. Only if all permissions are granted,
	 * permission also will be granted. If any permission has {@code shouldShowRationale} checked, than result also has it checked.
	 * <p>
	 * If one or several permissions have never been requested, invoke the related framework method
	 * to ask the user if he allows the permissions.
	 */
	public <T> ObservableTransformer<T, Permission> ensureEachCombined(final String... permissions) {
		return new ObservableTransformer<T, Permission>() {
			@Override
			public ObservableSource<Permission> apply(Observable<T> o) {
				return request(o, permissions)
					.buffer(permissions.length)
					.flatMap(new Function<List<Permission>, ObservableSource<Permission>>() {
						@Override
						public ObservableSource<Permission> apply(List<Permission> permissions) {
							if (permissions.isEmpty()) {
								return Observable.empty();
							}
							return Observable.just(new Permission(permissions));
						}
					});
			}
		};
	}

	/**
	 * Request permissions immediately, <b>must be invoked during initialization phase
	 * of your application</b>.
	 */
	@SuppressWarnings({"WeakerAccess", "unused"})
	public Observable<Boolean> request(final String... permissions) {
		return Observable.just(TRIGGER).compose(ensure(permissions));
	}

	public Observable<Boolean> requestSystemPermission(final SystemPermission systemPermission) {
		return Observable.just(TRIGGER).compose(ensure(systemPermission));
	}

	/**
	 * Request permissions immediately, <b>must be invoked during initialization phase
	 * of your application</b>.
	 */
	@SuppressWarnings({"WeakerAccess", "unused"})
	public Observable<Permission> requestEach(final String... permissions) {
		return Observable.just(TRIGGER).compose(ensureEach(permissions));
	}

	/**
	 * Request permissions immediately, <b>must be invoked during initialization phase
	 * of your application</b>.
	 */
	public Observable<Permission> requestEachCombined(final String... permissions) {
		return Observable.just(TRIGGER).compose(ensureEachCombined(permissions));
	}

	private Observable<Permission> request(final Observable<?> trigger, final String... permissions) {
		if (permissions == null || permissions.length == 0) {
			throw new IllegalArgumentException("RxPermissions.request/requestEach requires at least one input permission");
		}
		return oneOf(trigger, pending(permissions))
			.flatMap(new Function<Object, Observable<Permission>>() {
				@Override
				public Observable<Permission> apply(Object o) {
					return requestImplementation(permissions);
				}
			});
	}

	private Observable<?> pending(final String... permissions) {
		for (String p : permissions) {
			if (!mRxPermissionsFragment.get().containsByPermission(p)) {
				return Observable.empty();
			}
		}
		return Observable.just(TRIGGER);
	}

	private Observable<?> oneOf(Observable<?> trigger, Observable<?> pending) {
		if (trigger == null) {
			return Observable.just(TRIGGER);
		}
		return Observable.merge(trigger, pending);
	}

	@TargetApi(Build.VERSION_CODES.M)
	private Observable<Permission> requestImplementation(final String... permissions) {
		List<Observable<Permission>> list = new ArrayList<>(permissions.length);
		List<String> unrequestedPermissions = new ArrayList<>();

		// In case of multiple permissions, we create an Observable for each of them.
		// At the end, the observables are combined to have a unique response.
		for (String permission : permissions) {
			mRxPermissionsFragment.get().log("Requesting permission " + permission);
			if (isRuntimePermissionGranted(permission)) {
				// Already granted, or not Android M
				// Return a granted Permission object.
				list.add(Observable.just(new Permission(permission, true, false)));
				continue;
			}

			if (isRuntimePermissionRevoked(permission)) {
				// Revoked by a policy, return a denied Permission object.
				list.add(Observable.just(new Permission(permission, false, false)));
				continue;
			}

			PublishSubject<Permission> subject = mRxPermissionsFragment.get().getSubjectByPermission(permission);
			// Create a new subject if not exists
			if (subject == null) {
				unrequestedPermissions.add(permission);
				subject = PublishSubject.create();
				mRxPermissionsFragment.get().setSubjectForPermission(permission, subject);
			}

			if (unrequestedPermissions.isEmpty() && !isRuntimePermissionGranted(permission) && !isRuntimePermissionRevoked(permission)) {
				unrequestedPermissions.add(permission);
			}

			list.add(subject);
		}

		if (!unrequestedPermissions.isEmpty()) {
			String[] unrequestedPermissionsArray = unrequestedPermissions.toArray(new String[unrequestedPermissions.size()]);
			mRxPermissionsFragment.get().requestPermissions(unrequestedPermissionsArray);
		}
		return Observable.concat(Observable.fromIterable(list));
	}

	@TargetApi(Build.VERSION_CODES.M)
	private Observable<Permission> requestImplementation(final SystemPermission systemPermission) {

		mRxPermissionsFragment.get().log("Requesting permission " + systemPermission.getPermissionName());
		if (systemPermission.getEnabledChecker().invoke(mRxPermissionsFragment.get().getContext())) {
			// Already granted, or not Android M
			// Return a granted Permission object.
			return Observable.just(new Permission(systemPermission.getPermissionName(), true, false));
		}

		PublishSubject<Permission> subject = mRxPermissionsFragment.get().getSubjectByPermission(systemPermission.getPermissionName());
		// Create a new subject if not exists
		if (subject == null) {
			subject = PublishSubject.create();
			mRxPermissionsFragment.get().setSubjectForPermission(systemPermission.getPermissionName(), subject);
		}

		mRxPermissionsFragment.get().requestSystemPermission(systemPermission);


		return subject;
	}

	/**
	 * Invokes Activity.shouldShowRequestPermissionRationale and wraps
	 * the returned value in an observable.
	 * <p>
	 * In case of multiple permissions, only emits true if
	 * Activity.shouldShowRequestPermissionRationale returned true for
	 * all revoked permissions.
	 * <p>
	 * You shouldn't call this method if all permissions have been granted.
	 * <p>
	 * For SDK &lt; 23, the observable will always emit false.
	 */
	@SuppressWarnings("WeakerAccess")
	public Observable<Boolean> shouldShowRequestPermissionRationale(final Activity activity, final String... permissions) {
		if (!isMarshmallow()) {
			return Observable.just(false);
		}
		return Observable.just(shouldShowRequestPermissionRationaleImplementation(activity, permissions));
	}

	@TargetApi(Build.VERSION_CODES.M)
	private boolean shouldShowRequestPermissionRationaleImplementation(final Activity activity, final String... permissions) {
		for (String p : permissions) {
			if (!isRuntimePermissionGranted(p) && !activity.shouldShowRequestPermissionRationale(p)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true if the permission is already granted.
	 * <p>
	 * Always true if SDK &lt; 23.
	 */
	@SuppressWarnings("WeakerAccess")
	public boolean isRuntimePermissionGranted(String permission) {
		return !isMarshmallow() || mRxPermissionsFragment.get().isRuntimePermissionGranted(permission);
	}

	/**
	 * Returns true if the permission has been revoked by a policy.
	 * <p>
	 * Always false if SDK &lt; 23.
	 */
	@SuppressWarnings("WeakerAccess")
	public boolean isRuntimePermissionRevoked(String permission) {
		return isMarshmallow() && mRxPermissionsFragment.get().isRuntimePermissionRevoked(permission);
	}

	boolean isMarshmallow() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
	}

	void onRequestPermissionsResult(String permissions[], int[] grantResults) {
		mRxPermissionsFragment.get().onRequestPermissionsResult(permissions, grantResults, new boolean[permissions.length]);
	}

	@FunctionalInterface
	public interface Lazy<V> {
		V get();
	}

}
