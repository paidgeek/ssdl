import com.moybl.sidl.Lexer;
import com.moybl.sidl.Token;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.util.*;

@RunWith(Parameterized.class)
public class LexerTokensTest {

	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
				{
						"type enum use namespace // comment",
						Arrays.asList(
								Token.KEYWORD_TYPE,
								Token.KEYWORD_ENUM,
								Token.KEYWORD_USE,
								Token.KEYWORD_NAMESPACE)
				},
				{
						", [] {} 0 42/*this is a comment*/",
						Arrays.asList(
								Token.COMMA,
								Token.OPEN_BRACKET,
								Token.CLOSE_BRACKET,
								Token.OPEN_BRACE,
								Token.CLOSE_BRACE,
								Token.LITERAL_INTEGER,
								Token.LITERAL_INTEGER)
				},
				{
						"x s i i8 i16 i32 i64 u u8 u16 u32 u64 f32 f64 b",
						Arrays.asList(
								Token.IDENTIFIER,
								Token.TYPE_STRING,
								Token.TYPE_INT,
								Token.TYPE_INT8,
								Token.TYPE_INT16,
								Token.TYPE_INT32,
								Token.TYPE_INT64,
								Token.TYPE_UINT,
								Token.TYPE_UINT8,
								Token.TYPE_UINT16,
								Token.TYPE_UINT32,
								Token.TYPE_UINT64,
								Token.TYPE_FLOAT32,
								Token.TYPE_FLOAT64,
								Token.TYPE_BOOL
						)
				}
		});
	}

	private String input;
	private List<Token> expected;

	public LexerTokensTest(String input, List<Token> expected) {
		this.input = input;
		this.expected = expected;
	}

	@Test
	public void test() {
		Lexer lexer = new Lexer(new ByteArrayInputStream(input.getBytes()));

		for (int i = 0; i < expected.size(); i++) {
			Assert.assertEquals(expected.get(i), lexer.next().getToken());
		}

		Assert.assertEquals(Token.EOF, lexer.next().getToken());
	}

}