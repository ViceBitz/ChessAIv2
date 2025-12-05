import java.util.*;
/**
 * Stores the evaluation value along with if it's a lazy value or a full value
 * @author victor
 *
 */
public class EvaluationInfo
{
	public int value;
	public Vector<Integer> splits;
	
	public EvaluationInfo(int value)
	{
		this.value = value;
		this.splits = new Vector<>();
	}
	public EvaluationInfo(int value, Vector<Integer> splits)
	{
		this.value = value;
		this.splits = splits;
	}
}
