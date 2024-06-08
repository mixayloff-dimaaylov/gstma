# Ref: https://timstaley.co.uk/posts/making-git-and-jupyter-notebooks-play-nice/
# Ref[orig]: https://www.katzien.de/en/posts/2016-01-26-windows-dev-environment/
# based on https://gist.github.com/jfeist/cd00aa3b681092e1d5dc
def banner: "\(.) " + (28-(.|length))*"-";
# metadata
("Non-cell info" | banner), del(.cells), "",
# content
(.cells[] | (
     ("\(.cell_type) cell" | banner), 
     (.source[] | rtrimstr("\n")), # output source
     if ($show_output == "1") then # the cell output only when it is requested..
       "",
       (select(.cell_type=="code" and (.outputs|length)>0) | (
         ("output" | banner),
         (.outputs[] | (
            (select(.text) | "\(.text|add)" | rtrimstr("\n")),
            (select(.traceback) | (.traceback|join("\n"))),
            (select(.text or .traceback|not) | "(Non-plaintext output)")
		   )
         ),
         ""
		)
       )
     else 
       ""
     end
  )
)