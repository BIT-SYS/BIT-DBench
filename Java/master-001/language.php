<?php  if ( ! defined('_VALID_BBC')) exit('No direct script access allowed');

function lang_count()
{
	global $Bbc;
	if(isset($Bbc->lang_count)) {
		return $Bbc->lang_count;
	}else{
		global $db;
		$q = "SELECT COUNT(*) FROM bbc_lang";
		$Bbc->lang_count = $db->getOne($q);
		return $Bbc->lang_count;
	}
}

function get_lang()
{
	global $Bbc, $db;
	if(isset($Bbc->lang_array)) return $Bbc->lang_array;
	$file = 'lang/ref.cfg';
	$q = "SELECT id, LOWER(code) AS code FROM bbc_lang";
	$Bbc->lang_array = $db->cache('getAssoc', $q, $file);
	return $Bbc->lang_array;
}

function lang_assoc($id = 'id')
{
	global $Bbc, $db;
	if(isset($Bbc->lang_assoc[$id])) return $Bbc->lang_assoc[$id];
	$file = 'lang/ref_assoc_'.$id.'.cfg';
	$q = "SELECT $id AS d, id, code, title FROM bbc_lang";
	$Bbc->lang_assoc[$id] = $db->cache('getAssoc', $q, $file);
	return $Bbc->lang_assoc[$id];
}

function lang_id()
{
#	$output = (_ADMIN == '' && isset($_SESSION['lang_id']) ) ? $_SESSION['lang_id'] : (_ADMIN != '') ? 1 : @intval(config('rules','lang_default'));
	$output = (_ADMIN == '' && isset($_GET['lang_id']) && !empty(intval($_GET['lang_id']))) ? $_GET['lang_id'] : ((_ADMIN != '') ? 1 : @intval(config('rules','lang_default')));
	return $output;
}

function lang_fetch($module_id)
{
	global $Bbc, $db, $_LANG, $_CONFIG;
	$Bbc->lang_fetch = isset($Bbc->lang_fetch) ? $Bbc->lang_fetch : array();
	if(is_numeric($module_id))
	{
		if(in_array($module_id, $Bbc->lang_fetch)) return true;
		else $Bbc->lang_fetch[] = $module_id;
		$lang_id = lang_id();
		$_CONFIG['rules']['lang_default'] = $lang_id;
		$file = 'lang/'.$lang_id.'_'.$module_id.'.cfg';
		$q = "SELECT LOWER(c.code), t.content
					FROM bbc_lang_code AS c
					LEFT JOIN bbc_lang_text AS t ON(c.id=t.code_id AND t.lang_id=$lang_id)
					WHERE c.module_id=$module_id";
		$r = $db->cache('getAssoc', $q, $file);
		foreach($r AS $id => $content)
		{
			$_LANG[$id] = $content;
		}
		return true;
	}
	return false;
}

function lang_refresh()
{
	global $sys;
	$sys->clean_cache();
}

function lang($txt)
{
	global $_LANG, $_CONFIG;
	if (empty($txt))
	{
		return '';
	}
	$id = trim(strtolower($txt));
	if(isset($_LANG[$id]))
	{
		$output = $_LANG[$id];
	}else
	if(_ADMIN == '')
	{
		if($_CONFIG['rules']['lang_auto'] && !isset($_LANG[$id]))
		{
			global $db, $sys;
			$module_id = $sys->module_id;
			$code_id = $db->getOne("SELECT `id` FROM `bbc_lang_code` WHERE `code`='{$id}' AND `module_id`={$module_id}");
			if (empty($code_id))
			{
				$db->Execute("INSERT INTO `bbc_lang_code` SET `code`='$id', `module_id`={$module_id}");
				$code_id = $db->Insert_ID();
			}
			$r_lang = get_lang();
			foreach($r_lang AS $lang_id => $dt)
			{
				if((lang_id() == $lang_id))
				{
					$_LANG[$id] = $txt;
				}
				$content = $txt;
				$text_id = $db->getOne("SELECT `text_id` FROM `bbc_lang_text` WHERE `code_id`={$code_id} AND `lang_id`={$lang_id}");
				if (empty($text_id))
				{
					$db->Execute("INSERT INTO `bbc_lang_text` SET `lang_id`='{$lang_id}', `code_id`={$code_id}, `content`='{$content}'");
				}
			}
			lang_refresh();
		}
		$output = $txt;
	}else $output = $txt;
	$j = func_num_args();
	if ($j > 1)
	{
		$param = array($output);
		for($i=1;$i < $j;$i++)
		{
			$t = func_get_arg($i);
			if (is_array($t))
			{
				$param = array_merge($param, array_values($t));
			}else{
				$param[] = func_get_arg($i);
			}
		}
		$k = substr_count($output, '%');
		if ($k >= $j)
		{
			$l = $k - $j + 1;
			for ($i=0; $i < $l; $i++)
			{
				$param[] = '';
			}
		}
		$output = @call_user_func_array('sprintf', $param);
	}
	return $output;
}

function lang_sql($table, $id = '')
{
	$id = !empty($id) ? $id : $table.'_id';
	return '`'.$table.'` AS a LEFT JOIN `'.$table.'_text` AS t ON (t.`'.$id.'`=a.`id` AND t.`lang_id`='.lang_id().')';
}
