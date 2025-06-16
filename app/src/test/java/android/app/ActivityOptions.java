package android.app;

import android.os.Bundle;

public class ActivityOptions {
	public static ActivityOptions makeBasic() {
		return new ActivityOptions();
	}

	public Bundle toBundle() {
		return Bundle.EMPTY;
	}
}
