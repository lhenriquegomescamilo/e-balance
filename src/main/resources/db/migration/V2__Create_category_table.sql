-- V2: Create category table and link to transactions
CREATE TABLE IF NOT EXISTS category
(
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

-- Insert predefined categories (use NULL for id to let SQLite auto-generate)
INSERT INTO category (name) VALUES 
       ('Desconhecida'),
       ('Água'),
       ('Capsulas café'),
       ('Biscoitos'),
       ('Produtos de limpeza'),
       ('Escola dos filhos'),
       ('Material de escritório'),
       ('Restaurante'),
       ('Obra em casa'),
       ('Móveis de casa (menos cama)'),
       ('Decoração de casa'),
       ('Eletrodoméstico (menos máquina lavar/secar roupa)'),
       ('Telecomunicações'),
       ('Conta de Energia'),
       ('Passe transporte (marido/esposa e filhos)'),
       ('Hotéis'),
       ('Passagens aéreas'),
       ('Educação, livros, aulas, cursos material didático (filhos incluidos)'),
       ('Taxa bancária'),
       ('Plano de saúde'),
       ('Ingressos de parques'),
       ('Ingressos de cinema'),
       ('Ingressos de museu'),
       ('Vinho/whisky/bebidas brancas'),
       ('Táxi/uber'),
       ('Aluguer apartamento'),
       ('Material eletronico (televisao, telemovel, computador, etc)'),
       ('Contabilista'),
       ('Advogados'),
       ('Seguros'),
       ('Combustível'),
       ('Roupa');

-- Add category_id column to transactions
ALTER TABLE transactions ADD COLUMN category_id INTEGER DEFAULT 0;

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_transactions_category_id ON transactions(category_id);
