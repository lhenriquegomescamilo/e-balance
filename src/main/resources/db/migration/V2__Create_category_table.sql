-- V2: Create category table and link to transactions
CREATE TABLE IF NOT EXISTS category
(
    id         INTEGER PRIMARY KEY,
    name       TEXT NOT NULL,
    enum_name  TEXT NOT NULL UNIQUE
);

-- Insert predefined categories with explicit IDs matching the Category enum
INSERT INTO category (id, name, enum_name) VALUES 
       (0, 'Desconhecida', 'DESCONHECIDA'),
       (1, 'Moradia', 'MORADIA'),
       (2, 'Restaurante', 'RESTAURANTE'),
       (3, 'Biscoitos', 'BISCOITOS'),
       (4, 'Supermercado', 'SUPERMERCADO'),
       (5, 'Escola dos filhos', 'ESCOLA_DOS_FILHOS'),
       (6, 'Delivery', 'DELIVERY'),
       (7, 'Roupa', 'ROUPA'),
       (8, 'Eletrônicos', 'ELETRONICOS'),
       (9, 'Cabelo', 'CABELO'),
       (10, 'Transporte', 'TRANSPORTE'),
       (11, 'Carro', 'CARRO'),
       (12, 'Segurança Social', 'SEGURANCA_SOCIAL'),
       (13, 'IRS', 'IRS'),
       (14, 'Salário', 'SALARIO'),
       (15, 'Contabilista', 'CONTABILISTA'),
       (16, 'Arrendamento', 'ARRENDAMENTO'),
       (17, 'Telefonia', 'TELEFONIA'),
       (18, 'Energia', 'ENERGIA'),
       (19, 'Água', 'AGUA'),
       (20, 'Personal Trainer', 'PERSONAL_TRAINER'),
       (21, 'Barbeiro', 'BARBEIRO'),
       (22, 'Via Verde', 'VIA_VERDE'),
       (23, 'Seguro de carro', 'SEGURO_DE_CARRO'),
       (24, 'Doações', 'DOACOES'),
       (25, 'Assinaturas', 'ASSINATURAS'),
       (26, 'Taxa Bancária', 'TAXA_BANCARIA'),
       (27, 'Turismo', 'TURISMO'),
       (28, 'Portagem', 'PORTAGEM'),
       (29, 'Lazer', 'LAZER'),
       (30, 'Cartão', 'CARTAO'),
       (31, 'Estética', 'ESTETICA'),
       (32, 'Investimento', 'INVESTIMENTO'),
       (33, 'Farmácia', 'FARMACIA'),
       (34, 'Levantamento', 'LEVANTAMENTO'),
       (35, 'Lavanderia', 'LAVANDERIA'),
       (36, 'Combustível', 'COMBUSTIVEL'),
       (37, 'Hospital', 'HOSPITAL'),
       (38, 'Presente', 'PRESENTE'),
       (39, 'Lava Rápido', 'LAVA_RAPIDO'),
       (40, 'Imposto', 'IMPOSTO'),
       (41, 'Educação', 'EDUCACAO'),
       (42, 'Seguro', 'SEGURO'),
       (43, 'Transferências Brasil', 'TRANSFERENCIAS_BRASIL'),
       (44, 'Ginásio', 'GINASIO'),
       (45, 'Maternidade', 'MATERNIDADE'),
       (46, 'Transferências', 'TRANSFERENCIAS'),
       (47, 'Estacionamento', 'ESTACIONAMENTO');

-- Add category_id column to transactions
ALTER TABLE transactions ADD COLUMN category_id INTEGER DEFAULT 0;

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_transactions_category_id ON transactions(category_id);
