JAVAC = javac
JAVA = java

SRC_DIR = src
LIB_DIR = lib
BIN_DIR = bin

CORE_JAR = $(LIB_DIR)/core-4.5.2-d6b2bb5c76e6add4952f6559c87b5cb.jar

SOURCES = $(wildcard $(SRC_DIR)/*.java)

.PHONY: compile run clean

compile:
	mkdir -p $(BIN_DIR)
	$(JAVAC) -cp "$(CORE_JAR)" -d $(BIN_DIR) $(SOURCES)

run: compile
	$(JAVA) -cp "$(BIN_DIR):$(CORE_JAR)" Main

clean:
	rm -rf $(BIN_DIR)