package suite.lp.sewing;

import java.util.function.BiPredicate;

import suite.lp.Trail;
import suite.node.Node;

public interface SewingBinder extends SewingCloner {

	public static class BindEnv {
		public final Trail trail;
		public final Env env;

		public BindEnv(Trail trail, Env env) {
			this.trail = trail;
			this.env = env;
		}
	}

	public BiPredicate<BindEnv, Node> compileBind(Node node);

}
