package com.moybl.sidl;

import com.moybl.sidl.ast.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {

	private Lexer lexer;
	private Symbol current;
	private Symbol next;

	private List<String> currentPath;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
	}

	public Document parse() {
		next = lexer.next();

		Position a = next.getPosition();
		List<Definition> definitions = parseDefinitions();

		return new Document(Position.expand(a, current.getPosition()), definitions);
	}

	private List<Definition> parseDefinitions() {
		List<Definition> definitions = new ArrayList<Definition>();

		while (!match(Token.EOF)) {
			Definition definition = parseDefinition();

			if (definition != null) {
				definitions.add(definition);
			}
		}

		return definitions;
	}

	private Definition parseDefinition() {
		if (next.getToken() == Token.KEYWORD_NAMESPACE) {
			return parseNamespaceDefinition();
		}

		Definition def = null;
		List<Attribute> attributes = parseAttributeList();

		if (next.getToken() == Token.KEYWORD_TYPE) {
			def = parseTypeDefinition();
		} else if (next.getToken() == Token.KEYWORD_ENUM) {
			def = parseEnumDefinition();
		} else {
			throw ParserException.internal();
		}

		def.setAttributes(attributes);

		return def;
	}

	private NamespaceDefinition parseNamespaceDefinition() {
		check(Token.KEYWORD_NAMESPACE);
		Position a = current.getPosition();
		Identifier name = parseIdentifier();

		currentPath = name.getPath();

		return new NamespaceDefinition(Position.expand(a, current.getPosition()), name);
	}

	private TypeDefinition parseTypeDefinition() {
		check(Token.KEYWORD_TYPE);
		Position a = current.getPosition();
		Identifier name = parseIdentifier();

		if (match(Token.IDENTIFIER)) {
			Identifier oldName = parseIdentifier();

			return new TypeDefinition(Position.expand(a, oldName.getPosition()), name, oldName);
		} else if (next.getToken().isType()) {
			Type type = parseType();

			return new TypeDefinition(Position.expand(a, type.getPosition()), name, type);
		}

		List<Field> fields = parseFieldList();
		Position b = null;
		if (fields.size() != 0) {
			b = Position
					.expand(fields.get(0).getPosition(), fields.get(fields.size() - 1)
							.getPosition());
		} else {
			b = current.getPosition();
		}

		return new TypeDefinition(Position.expand(a, b), name, fields);
	}

	private EnumDefinition parseEnumDefinition() {
		Position b = null;
		Token type = Token.TYPE_UINT8;

		check(Token.KEYWORD_ENUM);
		Position a = current.getPosition();
		Identifier name = parseIdentifier();

		if (next.getToken().isIntegerType()) {
			check(next.getToken());
			type = current.getToken();
		}

		List<EnumValue> values = parseEnumList();

		if (values.size() != 0) {
			b = Position
					.expand(values.get(0).getPosition(), values.get(values.size() - 1)
							.getPosition());
		} else {
			b = current.getPosition();
		}

		return new EnumDefinition(Position.expand(a, b), name, type, values);
	}

	private List<EnumValue> parseEnumList() {
		check(Token.OPEN_BRACE);
		List<EnumValue> values = new ArrayList<EnumValue>();

		long next = 0;

		while (match(Token.IDENTIFIER)) {
			EnumValue ev = parseEnumValue();

			if (ev.getValue() != null && ev.getValue().getKind() != Literal.Kind.INTEGER) {
				throw ParserException.expectedIntegerType(current.getPosition(), current.getToken());
			}

			if (ev.getValue() == null) {
				ev.setValue(new Literal(ev.getPosition(), Literal.Kind.INTEGER, next));
				next++;
			} else {
				long val = ev.getValue().getLongValue();

				if (next != 0 && val <= next) {
					throw ParserException.enumInvalidOrder(ev.getPosition());
				}

				next = val + 1;
			}

			values.add(ev);
			accept(Token.COMMA);
		}

		check(Token.CLOSE_BRACE);

		return values;
	}

	private EnumValue parseEnumValue() {
		check(Token.IDENTIFIER);
		String name = current.getLexeme();
		Position a = current.getPosition();
		Literal value = null;

		if (accept(Token.EQUALS)) {
			value = parseLiteral();
		}

		return new EnumValue(Position.expand(a, current.getPosition()), name, value);
	}

	private Field parseField() {
		check(Token.IDENTIFIER);
		String name = current.getLexeme();
		Position a = current.getPosition();

		Type type = parseType();

		return new Field(Position.expand(a, type.getPosition()), name, type);
	}

	private List<Field> parseFieldList() {
		check(Token.OPEN_BRACE);
		List<Field> fields = new ArrayList<Field>();

		while (match(Token.IDENTIFIER)) {
			fields.add(parseField());
			accept(Token.COMMA);
		}

		check(Token.CLOSE_BRACE);

		return fields;
	}

	private Type parseType() {
		if (accept(Token.OPEN_BRACKET)) {
			Position a = current.getPosition();

			if (accept(Token.LITERAL_INTEGER)) {
				int length = Integer.parseInt(current.getLexeme());
				check(Token.CLOSE_BRACKET);

				return new ArrayType(Position
						.expand(a, current.getPosition()), length, parsePrimaryType());
			} else {
				check(Token.CLOSE_BRACKET);

				return new ListType(Position.expand(a, current.getPosition()), parsePrimaryType());
			}
		}

		return parsePrimaryType();
	}

	private PrimaryType parsePrimaryType() {
		if (accept(Token.ASTERISK)) {
			Position a = current.getPosition();
			Identifier name = parseIdentifier();

			return new PrimaryType(Position.expand(a, name.getPosition()), name, true);
		}

		if (match(Token.IDENTIFIER) | match(Token.COLON)) {
			Identifier name = parseAccessIdentifier();

			return new PrimaryType(name.getPosition(), name, false);
		}

		if (!next.getToken().isType()) {
			throw ParserException.expectedType(next.getPosition(), next.getToken());
		}

		check(next.getToken());

		return new PrimaryType(current.getPosition(), current.getToken(), false);
	}

	private List<Attribute> parseAttributeList() {
		List<Attribute> attributes = new ArrayList<Attribute>();

		while (match(Token.AT)) {
			attributes.add(parseAttribute());
		}

		return attributes;
	}

	private Attribute parseAttribute() {
		check(Token.AT);
		Position a = current.getPosition();
		check(Token.IDENTIFIER);
		String name = current.getLexeme();

		List<AttributeEntry> entries = new ArrayList<AttributeEntry>();

		if (accept(Token.OPEN_PARENTHESIS)) {
			while (match(Token.IDENTIFIER) || next.getToken().isLiteral()) {
				entries.add(parseAttributeEntry());
				accept(Token.COMMA);
			}

			check(Token.CLOSE_PARENTHESIS);
		}

		return new Attribute(Position.expand(a, current.getPosition()), name, entries);
	}

	private AttributeEntry parseAttributeEntry() {
		String name = null;
		Position a = null;

		if (accept(Token.IDENTIFIER)) {
			a = current.getPosition();
			name = current.getLexeme();
			check(Token.EQUALS);
		}

		Literal value = parseLiteral();

		if (a == null) {
			a = current.getPosition();
		}

		return new AttributeEntry(Position.expand(a, current.getPosition()), name, value);
	}

	private Literal parseLiteral() {
		Literal.Kind kind = Literal.Kind.INTEGER;
		Object value = null;

		if (accept(Token.LITERAL_INTEGER)) {
			kind = Literal.Kind.INTEGER;
			value = Long.parseLong(current.getLexeme());
		} else if (accept(Token.LITERAL_STRING)) {
			kind = Literal.Kind.STRING;
			value = current.getLexeme().substring(1, current.getLexeme().length() - 1);
		} else if (accept(Token.LITERAL_FLOAT)) {
			kind = Literal.Kind.FLOAT;
			value = Double.parseDouble(current.getLexeme());
		} else {
			throw ParserException.expectedLiteral(current.getPosition(), current.getToken());
		}

		return new Literal(current.getPosition(), kind, value);
	}

	private Identifier parseIdentifier() {
		Position a = next.getPosition();
		List<String> path = new ArrayList<String>();

		if (currentPath != null) {
			path.addAll(currentPath);
		}

		while (accept(Token.IDENTIFIER)) {
			path.add(current.getLexeme());

			if (!accept(Token.COLON)) {
				break;
			}
		}

		return new Identifier(Position.expand(a, current.getPosition()), path);
	}

	private Identifier parseAccessIdentifier() {
		Position a = next.getPosition();
		List<String> path = new ArrayList<String>();

		if (accept(Token.COLON)) {
			if (currentPath != null) {
				path.addAll(currentPath);
			}
		}

		while (accept(Token.IDENTIFIER)) {
			path.add(current.getLexeme());

			if (!accept(Token.COLON)) {
				break;
			}
		}

		if (path.size() == 1 && currentPath != null) {
			path.addAll(0, currentPath);
		}

		return new Identifier(Position.expand(a, current.getPosition()), path);
	}

	private boolean match(Token token) {
		return next.getToken() == token;
	}

	private boolean match(Token... tokens) {
		for (int i = 0; i < tokens.length; i++) {
			if (match(tokens[i])) {
				return true;
			}
		}

		return false;
	}

	private void check(Token token) {
		if (match(token)) {
			current = next;
			next = lexer.next();
		} else {
			throw ParserException.unexpected(current.getPosition(), token, next.getToken());
		}
	}

	private void check(Token... tokens) {
		for (int i = 0; i < tokens.length; i++) {
			check(tokens[i]);
		}
	}

	private boolean accept(Token token) {
		if (match(token)) {
			current = next;
			next = lexer.next();

			return true;
		}

		return false;
	}

	private boolean accept(Token... tokens) {
		for (int i = 0; i < tokens.length; i++) {
			if (accept(tokens[i])) {
				return true;
			}
		}

		return false;
	}

}
